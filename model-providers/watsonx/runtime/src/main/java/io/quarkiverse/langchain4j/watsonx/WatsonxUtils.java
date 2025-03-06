package io.quarkiverse.langchain4j.watsonx;

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.internal.Utils;
import io.quarkiverse.langchain4j.watsonx.bean.CosError;
import io.quarkiverse.langchain4j.watsonx.bean.WatsonxError;
import io.quarkiverse.langchain4j.watsonx.exception.BuiltinServiceException;
import io.quarkiverse.langchain4j.watsonx.exception.COSException;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonxException;

public class WatsonxUtils {

    public static <T> T retryOn(Callable<T> action) {
        int maxAttempts = 1;
        for (int i = 0; i <= maxAttempts; i++) {

            try {
                return action.call();
            } catch (WatsonxException | BuiltinServiceException | COSException e) {

                if (!isTokenExpired(e))
                    throw e;

            } catch (WebApplicationException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Failed after " + maxAttempts + " attempts");
    }

    public static boolean isTokenExpired(Throwable exception) {

        if (exception instanceof WatsonxException e) {

            if (e.details() == null || e.details().errors() == null || e.details().errors().size() == 0) {
                return false;
            }

            Optional<WatsonxError.Code> optional = Optional.empty();
            for (WatsonxError.Error error : e.details().errors()) {

                var c = error.codeToEnum();
                if (c.isPresent() && WatsonxError.Code.AUTHENTICATION_TOKEN_EXPIRED.equals(c.get())) {
                    optional = Optional.of(c.get());
                    break;
                }
            }

            if (optional.isPresent()) {
                return true;
            }

        } else if (exception instanceof BuiltinServiceException e) {

            if (e.statusCode() == Status.UNAUTHORIZED.getStatusCode() &&
                    e.details().equalsIgnoreCase("jwt expired")) {
                return true;
            }

        } else if (exception instanceof COSException e) {

            if (e.statusCode() == Status.FORBIDDEN.getStatusCode()
                    && e.details().getCode().equals(CosError.Code.ACCESS_DENIED)) {
                return true;
            }
        }

        return false;
    }

    public static String base64Image(Image image) {

        if (Objects.nonNull(image.base64Data()))
            return image.base64Data();

        try {
            byte[] bytes = switch (image.url().getScheme()) {
                case "http", "https", "file" -> Utils.readBytes(image.url().toString());
                default -> throw new RuntimeException("The only supported image schemes are: [http, https, file]");
            };
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Error converting the image to base64, see the log for more details", e);
        }
    }
}
