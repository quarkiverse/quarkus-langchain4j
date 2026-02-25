package io.quarkiverse.langchain4j.watsonx.runtime.client;

import static java.util.Objects.nonNull;

import java.time.Duration;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.ibm.watsonx.ai.WatsonxRestClient;
import com.ibm.watsonx.ai.core.HttpUtils;
import com.ibm.watsonx.ai.core.exception.WatsonxException;
import com.ibm.watsonx.ai.core.exception.model.WatsonxError;
import com.ibm.watsonx.ai.core.http.BaseHttpClient;
import com.ibm.watsonx.ai.core.http.interceptors.RetryInterceptor;

import io.quarkus.logging.Log;

public final class WatsonxRestClientUtils {

    static final String TRANSACTION_ID_HEADER = WatsonxRestClient.TRANSACTION_ID_HEADER;
    static final String REQUEST_ID_HEADER = BaseHttpClient.REQUEST_ID_HEADER;

    private static final Predicate<Throwable> TOKEN_EXPIRED = RetryInterceptor.ON_TOKEN_EXPIRED.retryOn().get(0).predicate()
            .orElseThrow();
    private static final Predicate<Throwable> ON_RETRYABLE_STATUS_CODES = RetryInterceptor.ON_RETRYABLE_STATUS_CODES.retryOn()
            .get(0)
            .predicate().orElseThrow();

    private WatsonxRestClientUtils() {
    }

    static WatsonxException responseToWatsonxException(Response response) {
        MediaType mediaType = response.getMediaType();
        String body = response.readEntity(String.class);

        if (body.isEmpty())
            return new WatsonxException("Status code: " + response.getStatus(), response.getStatus(), null);

        var error = HttpUtils.parseErrorBody(response.getStatus(), body, mediaType.toString());
        var joiner = new StringJoiner("\n");

        if (nonNull(error.errors()) && error.errors().size() > 0) {
            for (WatsonxError.Error errorDetail : error.errors())
                joiner.add("%s: %s".formatted(errorDetail.code(), errorDetail.message()));
        }

        return new WatsonxException(joiner.toString(), response.getStatus(), error);
    }

    public static boolean shouldRetry(Throwable e) {
        return TOKEN_EXPIRED.test(e) || ON_RETRYABLE_STATUS_CODES.test(e);
    }

    public static <T> T retryOn(String requestId, Callable<T> action) {
        int maxRetries = 10;
        var timeout = Duration.ofMillis(20);

        for (int i = 1; i <= maxRetries; i++) {

            try {

                return action.call();

            } catch (WatsonxException e) {

                if (shouldRetry(e)) {
                    try {

                        Log.debugf(
                                "Retrying request \"%s\" (%s/%s) after failure: %s",
                                requestId, i, maxRetries, e.getMessage());

                        Thread.sleep(timeout.toMillis());

                    } catch (Exception ex) {
                        throw new RuntimeException(e);
                    }
                    timeout = timeout.multipliedBy(2);
                    continue;
                }

                throw e;

            } catch (WebApplicationException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("Request failed after " + maxRetries + " attempts");
    }
}
