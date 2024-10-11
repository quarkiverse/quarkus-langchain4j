package io.quarkiverse.langchain4j.watsonx;

import java.util.Optional;
import java.util.concurrent.Callable;

import jakarta.ws.rs.WebApplicationException;

import io.quarkiverse.langchain4j.watsonx.bean.WatsonxError;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonxException;

public class WatsonxUtils {

    public static <T> T retryOn(Callable<T> action) {
        int maxAttempts = 1;
        for (int i = 0; i <= maxAttempts; i++) {

            try {

                return action.call();

            } catch (WatsonxException e) {

                if (e.details() == null || e.details().errors() == null || e.details().errors().size() == 0)
                    throw e;

                Optional<WatsonxError.Code> optional = Optional.empty();
                for (WatsonxError.Error error : e.details().errors()) {

                    var c = error.codeToEnum();
                    if (c.isPresent() && WatsonxError.Code.AUTHENTICATION_TOKEN_EXPIRED.equals(c.get())) {
                        optional = Optional.of(c.get());
                        break;
                    }
                }

                if (!optional.isPresent())
                    throw e;

            } catch (WebApplicationException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Failed after " + maxAttempts + " attempts");
    }
}
