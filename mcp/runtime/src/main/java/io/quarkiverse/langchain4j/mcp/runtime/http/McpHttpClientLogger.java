package io.quarkiverse.langchain4j.mcp.runtime.http;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.ClientLogger;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

class McpHttpClientLogger implements ClientLogger {
    private static final Logger log = Logger.getLogger(McpHttpClientLogger.class);

    private final boolean logRequests;
    private final boolean logResponses;

    public McpHttpClientLogger(boolean logRequests, boolean logResponses) {
        this.logRequests = logRequests;
        this.logResponses = logResponses;
    }

    @Override
    public void setBodySize(int bodySize) {
        // ignore
    }

    @Override
    public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
        if (logRequests && log.isInfoEnabled()) {
            try {
                log.infof("Request:\n- method: %s\n- url: %s\n- headers: %s\n- body: %s", request.getMethod(),
                        request.absoluteURI(), inOneLine(request.headers()), bodyToString(body));
            } catch (Exception e) {
                log.warn("Failed to log request", e);
            }
        }
    }

    @Override
    public void logResponse(HttpClientResponse response, boolean redirect) {
        if (logResponses && log.isInfoEnabled()) {
            response.bodyHandler(new Handler<>() {
                @Override
                public void handle(Buffer body) {
                    try {
                        log.infof("Response:\n- status code: %s\n- headers: %s\n- body: %s", response.statusCode(),
                                inOneLine(response.headers()), bodyToString(body));
                    } catch (Exception e) {
                        log.warn("Failed to log response", e);
                    }
                }
            });
        }
    }

    private String inOneLine(MultiMap headers) {
        return stream(headers.spliterator(), false)
                .map(header -> {
                    var headerKey = header.getKey();
                    var headerValue = header.getValue();
                    return "[%s: %s]".formatted(headerKey, headerValue);
                })
                .collect(joining(", "));
    }

    private String bodyToString(Buffer body) {
        return (body != null) ? body.toString() : "";
    }

}
