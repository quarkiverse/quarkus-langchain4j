package io.quarkiverse.langchain4j.mcp.runtime.http;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.LoggingScope;
import org.jboss.resteasy.reactive.server.jackson.JacksonBasicMessageBodyReader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.mcp.client.protocol.CancellationNotification;
import dev.langchain4j.mcp.client.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.client.protocol.McpClientMessage;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.client.transport.McpTransport;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class QuarkusHttpMcpTransport implements McpTransport {

    private static final Logger log = Logger.getLogger(QuarkusHttpMcpTransport.class);
    private final String sseUrl;
    private final McpSseEndpoint sseEndpoint;
    private final Duration timeout;
    private final boolean logResponses;
    private final boolean logRequests;
    private SseSubscriber mcpSseEventListener;
    private final Map<Long, CompletableFuture<JsonNode>> pendingOperations = new ConcurrentHashMap<>();

    // this is obtained from the server after initializing the SSE channel
    private volatile String postUrl;
    private volatile McpPostEndpoint postEndpoint;

    public QuarkusHttpMcpTransport(QuarkusHttpMcpTransport.Builder builder) {
        sseUrl = ensureNotNull(builder.sseUrl, "Missing SSE endpoint URL");
        timeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));

        this.logRequests = builder.logRequests;
        this.logResponses = builder.logResponses;

        QuarkusRestClientBuilder clientBuilder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(builder.sseUrl))
                .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .loggingScope(LoggingScope.ALL)
                .register(new JacksonBasicMessageBodyReader(new ObjectMapper()));
        if (logRequests || logResponses) {
            clientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            clientBuilder.clientLogger(new McpHttpClientLogger(logRequests, logResponses));
        }
        sseEndpoint = clientBuilder.build(McpSseEndpoint.class);
    }

    @Override
    public void start() {
        mcpSseEventListener = startSseChannel(logResponses);
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(postUrl))
                .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .register(new JacksonBasicMessageBodyReader(new ObjectMapper()));
        if (logRequests || logResponses) {
            builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            builder.clientLogger(new McpHttpClientLogger(logRequests, logResponses));
        }
        postEndpoint = builder
                .build(McpPostEndpoint.class);
    }

    @Override
    public JsonNode initialize(McpInitializeRequest request) {
        return executeAndWait(request, request.getId());
    }

    @Override
    public JsonNode listTools(McpListToolsRequest operation) {
        return executeAndWait(operation, operation.getId());
    }

    @Override
    public JsonNode executeTool(McpCallToolRequest operation, Duration timeout) throws TimeoutException {
        return executeAndWait(operation, operation.getId(), timeout);
    }

    private JsonNode executeAndWait(McpClientMessage request, Long id) {
        try {
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pendingOperations.put(id, future);
            try (Response response = postEndpoint.post(request)) {
                int statusCode = response.getStatus();
                if (!isExpectedStatusCode(statusCode)) {
                    throw new RuntimeException("Unexpected status code: " + statusCode);
                }
            }
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(id);
        }
    }

    private boolean isExpectedStatusCode(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private JsonNode executeAndWait(McpClientMessage request, Long id, Duration timeout) throws TimeoutException {
        try {
            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pendingOperations.put(id, future);
            try (Response response = postEndpoint.post(request)) {
                int statusCode = response.getStatus();
                if (!isExpectedStatusCode(statusCode)) {
                    throw new RuntimeException("Unexpected status code: {}" + statusCode);
                }
            }
            long timeoutMillis = timeout.toMillis() == 0 ? Long.MAX_VALUE : timeout.toMillis();
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            CancellationNotification cancellationNotification = new CancellationNotification(id, "Timeout");
            try (Response response = postEndpoint.post(cancellationNotification)) {
                if (!isExpectedStatusCode(response.getStatus())) {
                    log.warn("Failed to send cancellation notification, the server returned: " + response.getStatus());
                }
            }
            throw timeoutException;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            pendingOperations.remove(id);
        }
    }

    private SseSubscriber startSseChannel(boolean logResponses) {
        CompletableFuture<String> initializationFinished = new CompletableFuture<>();
        SseSubscriber listener = new SseSubscriber(pendingOperations, logResponses, initializationFinished);
        sseEndpoint.get().subscribe().with(listener, throwable -> {
            if (!initializationFinished.isDone()) {
                initializationFinished.completeExceptionally(throwable);
            }
        });
        // wait for the SSE channel to be created, receive the POST url from the server, throw an exception if that
        // failed
        try {
            long timeoutMillis = this.timeout.toMillis() > 0 ? this.timeout.toMillis() : Integer.MAX_VALUE;
            String relativePostUrl = initializationFinished.get(timeoutMillis, TimeUnit.MILLISECONDS);
            postUrl = buildAbsolutePostUrl(relativePostUrl);
            log.debug("Received the server's POST URL: " + postUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return listener;
    }

    private String buildAbsolutePostUrl(String relativePostUrl) {
        try {
            return URI.create(this.sseUrl).resolve(relativePostUrl).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {

    }

    public static class Builder {

        private String sseUrl;
        private Duration timeout;
        private boolean logRequests = false;
        private boolean logResponses = false;

        /**
         * The initial URL where to connect to the server and request a SSE
         * channel.
         */
        public QuarkusHttpMcpTransport.Builder sseUrl(String sseUrl) {
            this.sseUrl = sseUrl;
            return this;
        }

        public QuarkusHttpMcpTransport.Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public QuarkusHttpMcpTransport.Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public QuarkusHttpMcpTransport.Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public QuarkusHttpMcpTransport build() {
            return new QuarkusHttpMcpTransport(this);
        }
    }

}
