package io.quarkiverse.langchain4j.tavily;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.function.BiConsumer;

import org.jboss.logging.Logger;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import io.quarkiverse.langchain4j.runtime.SensitiveHeaderMasker;
import io.vertx.core.buffer.Buffer;

class TavilyLoggingHttpClient implements HttpClient {

    private static final Logger log = Logger.getLogger(TavilyLoggingHttpClient.class);

    private final HttpClient delegate;
    private final boolean logRequests;
    private final boolean logResponses;

    TavilyLoggingHttpClient(HttpClient delegate, boolean logRequests, boolean logResponses) {
        this.delegate = delegate;
        this.logRequests = logRequests;
        this.logResponses = logResponses;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        logRequest(request);
        try {
            SuccessfulHttpResponse response = delegate.execute(request);
            logResponse(response);
            return response;
        } catch (HttpException e) {
            logFailure(e);
            throw e;
        }
    }

    @Override
    public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
        logRequest(request);
        CompletableFuture<SuccessfulHttpResponse> httpFuture = delegate.executeAsync(request);
        CompletableFuture<SuccessfulHttpResponse> result = httpFuture
                .whenComplete(new BiConsumer<SuccessfulHttpResponse, Throwable>() {
                    @Override
                    public void accept(SuccessfulHttpResponse response, Throwable error) {
                        if (error == null) {
                            logResponse(response);
                            return;
                        }
                        Throwable cause = error instanceof CompletionException && error.getCause() != null
                                ? error.getCause()
                                : error;
                        if (cause instanceof HttpException httpException) {
                            logFailure(httpException);
                        }
                    }
                });
        propagateCancellation(result, httpFuture);
        return result;
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventListener listener) {
        delegate.execute(request, listener);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        delegate.execute(request, parser, listener);
    }

    @Override
    public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request) {
        return delegate.stream(request);
    }

    @Override
    public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
        return delegate.stream(request, parser);
    }

    private void logRequest(HttpRequest request) {
        if (!logRequests || !log.isInfoEnabled()) {
            return;
        }
        try {
            log.infof("Request:\n- method: %s\n- url: %s\n- headers: %s\n- body: %s",
                    request.method(),
                    request.url(),
                    inOneLine(request.headers()),
                    shadowBody(request.body()));
        } catch (Exception e) {
            log.warn("Failed to log request", e);
        }
    }

    private void logResponse(SuccessfulHttpResponse response) {
        if (!logResponses || !log.isInfoEnabled()) {
            return;
        }
        try {
            log.infof("Response:\n- status code: %s\n- headers: %s\n- body: %s",
                    response.statusCode(),
                    inOneLine(response.headers()),
                    shadowBody(response.body()));
        } catch (Exception e) {
            log.warn("Failed to log response", e);
        }
    }

    private void logFailure(HttpException e) {
        if (!logResponses || !log.isInfoEnabled()) {
            return;
        }
        try {
            log.infof("Response:\n- status code: %s\n- body: %s",
                    e.statusCode(),
                    shadowBody(e.getMessage()));
        } catch (Exception ex) {
            log.warn("Failed to log response", ex);
        }
    }

    private static String shadowBody(String body) {
        if (body == null) {
            return "";
        }
        return ShadowSensitiveData.process(Buffer.buffer(body), "api_key");
    }

    private static String inOneLine(Map<String, List<String>> headers) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            parts.add(String.format("[%s: %s]", header.getKey(), maskedValue(header.getKey(), header.getValue())));
        }
        return String.join(", ", parts);
    }

    private static String maskedValue(String headerName, List<String> values) {
        String value = String.join(", ", values);
        if (SensitiveHeaderMasker.isSensitive(headerName)) {
            return SensitiveHeaderMasker.mask(value);
        }
        return value;
    }
}
