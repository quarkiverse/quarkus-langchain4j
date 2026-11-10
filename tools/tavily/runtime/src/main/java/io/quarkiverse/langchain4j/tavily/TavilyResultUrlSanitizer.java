package io.quarkiverse.langchain4j.tavily;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.function.Function;

import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriBuilderException;

import org.jboss.logging.Logger;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Drops Tavily search results whose URL upstream cannot map, so one bad URL does not fail the whole search.
 * <p>
 * Temporary workaround for langchain4j/langchain4j#6947. Once that fix is released and {@code langchain4j.version}
 * is bumped:
 * <ul>
 * <li>delete this class and its wiring in {@link TavilyHttpClientBuilder#build()}</li>
 * <li>update {@code TavilyResultUrlTest}: upstream leaves non-ASCII paths such as {@code /café} unencoded</li>
 * </ul>
 */
class TavilyResultUrlSanitizer implements HttpClient {

    private static final Logger log = Logger.getLogger(TavilyResultUrlSanitizer.class);

    private final HttpClient delegate;

    TavilyResultUrlSanitizer(HttpClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        return sanitize(delegate.execute(request));
    }

    @Override
    public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
        CompletableFuture<SuccessfulHttpResponse> httpFuture = delegate.executeAsync(request);
        CompletableFuture<SuccessfulHttpResponse> result = httpFuture
                .thenApply(new Function<SuccessfulHttpResponse, SuccessfulHttpResponse>() {
                    @Override
                    public SuccessfulHttpResponse apply(SuccessfulHttpResponse response) {
                        return sanitize(response);
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

    private static SuccessfulHttpResponse sanitize(SuccessfulHttpResponse response) {
        try {
            JsonObject body = new JsonObject(response.body());
            JsonArray results = body.getJsonArray("results");
            if (results == null) {
                return response;
            }
            body.put("results", sanitizedResults(results));
            return SuccessfulHttpResponse.builder()
                    .statusCode(response.statusCode())
                    .headers(headersWithoutContentLength(response.headers()))
                    .body(body.encode())
                    .build();
        } catch (RuntimeException e) {
            log.debugf(e, "Tavily response could not be sanitized, passing it through unchanged");
            return response;
        }
    }

    private static JsonArray sanitizedResults(JsonArray results) {
        JsonArray validResults = new JsonArray();
        for (Object item : results) {
            JsonObject result = item instanceof JsonObject ? (JsonObject) item : new JsonObject();
            String url = normalizedUrl(result.getValue("url"));
            if (url == null) {
                log.warnf("Skipping Tavily search result with invalid URL: %s", result.getValue("url"));
                continue;
            }
            result.put("url", url);
            validResults.add(result);
        }
        return validResults;
    }

    private static String normalizedUrl(Object rawUrl) {
        if (!(rawUrl instanceof String)) {
            return null;
        }
        try {
            return toUri((String) rawUrl).toASCIIString();
        } catch (IllegalArgumentException | UriBuilderException e) {
            return null;
        }
    }

    private static URI toUri(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            uri = UriBuilder.newInstance().uri(url).build();
        }
        return URI.create(uri.toASCIIString());
    }

    private static Map<String, List<String>> headersWithoutContentLength(Map<String, List<String>> headers) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (!"content-length".equalsIgnoreCase(header.getKey())) {
                copy.put(header.getKey(), header.getValue());
            }
        }
        return copy;
    }
}
