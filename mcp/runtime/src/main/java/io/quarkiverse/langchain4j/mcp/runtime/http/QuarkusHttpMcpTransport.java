package io.quarkiverse.langchain4j.mcp.runtime.http;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.LoggingScope;
import org.jboss.resteasy.reactive.server.jackson.JacksonBasicMessageBodyReader;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.mcp.client.protocol.CancellationNotification;
import dev.langchain4j.mcp.client.protocol.McpCallToolRequest;
import dev.langchain4j.mcp.client.protocol.McpClientMessage;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.protocol.McpListToolsRequest;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class QuarkusHttpMcpTransport implements McpTransport {

    private static final Logger log = Logger.getLogger(QuarkusHttpMcpTransport.class);
    private final String sseUrl;
    private final McpSseEndpoint sseEndpoint;
    private final Duration timeout;
    private final boolean logResponses;
    private final boolean logRequests;
    private SseSubscriber mcpSseEventListener;

    // this is obtained from the server after initializing the SSE channel
    private volatile String postUrl;
    private volatile McpPostEndpoint postEndpoint;
    private volatile McpOperationHandler operationHandler;

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
                .register(new JacksonBasicMessageBodyReader(QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER));
        if (logRequests || logResponses) {
            clientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            clientBuilder.clientLogger(new McpHttpClientLogger(logRequests, logResponses));
        }
        sseEndpoint = clientBuilder.build(McpSseEndpoint.class);
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        this.operationHandler = messageHandler;
        mcpSseEventListener = startSseChannel(logResponses);
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(postUrl))
                .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .register(new JacksonBasicMessageBodyReader(QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER));
        if (logRequests || logResponses) {
            builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            builder.clientLogger(new McpHttpClientLogger(logRequests, logResponses));
        }
        postEndpoint = builder
                .build(McpPostEndpoint.class);
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest request) {
        return execute(request, request.getId());
    }

    @Override
    public CompletableFuture<JsonNode> listTools(McpListToolsRequest operation) {
        return execute(operation, operation.getId());
    }

    @Override
    public void cancelOperation(long operationId) {
        CancellationNotification cancellationNotification = new CancellationNotification(operationId, "Timeout");
        execute(cancellationNotification, null);
    }

    @Override
    public CompletableFuture<JsonNode> executeTool(McpCallToolRequest operation) {
        return execute(operation, operation.getId());
    }

    private CompletableFuture<JsonNode> execute(McpClientMessage request, Long id) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (id != null) {
            operationHandler.startOperation(id, future);
        }
        postEndpoint.post(request).onItem().invoke(response -> {
            int statusCode = response.getStatus();
            if (!isExpectedStatusCode(statusCode)) {
                throw new RuntimeException("Unexpected status code: " + statusCode);
            }
        }).subscribeAsCompletionStage();
        return future;
    }

    private boolean isExpectedStatusCode(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private SseSubscriber startSseChannel(boolean logResponses) {
        CompletableFuture<String> initializationFinished = new CompletableFuture<>();
        SseSubscriber listener = new SseSubscriber(operationHandler, logResponses, initializationFinished);
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
