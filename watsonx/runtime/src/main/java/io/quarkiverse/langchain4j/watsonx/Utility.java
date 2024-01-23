package io.quarkiverse.langchain4j.watsonx;

import java.util.Optional;
import java.util.concurrent.Callable;

import jakarta.ws.rs.WebApplicationException;

import io.quarkiverse.langchain4j.watsonx.bean.WatsonError;
import io.quarkiverse.langchain4j.watsonx.exception.WatsonException;

public class Utility {

    public static <T> T retryOn(Callable<T> action) {

        int maxAttempts = 1;
        for (int i = 0; i <= maxAttempts; i++) {

            try {

                return action.call();

            } catch (WatsonException e) {

                if (e.details() == null || e.details().errors() == null || e.details().errors().size() == 0)
                    throw e;

                Optional<WatsonError.Code> optional = Optional.empty();
                for (WatsonError.Error error : e.details().errors()) {
                    if (WatsonError.Code.AUTHENTICATION_TOKEN_EXPIRED.equals(error.code())) {
                        optional = Optional.of(error.code());
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
