package io.quarkiverse.langchain4j.mcp.runtime.http;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.api.LoggingScope;
import org.jboss.resteasy.reactive.server.jackson.JacksonBasicMessageBodyReader;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.tls.TlsConfiguration;
import io.smallrye.mutiny.Uni;

public class QuarkusHttpMcpTransport implements McpTransport {

    private static final Logger log = Logger.getLogger(QuarkusHttpMcpTransport.class);
    private final String sseUrl;
    private final McpSseEndpoint sseEndpoint;
    private final Duration timeout;
    private final boolean logResponses;
    private final boolean logRequests;
    private final TlsConfiguration tlsConfiguration;

    // this is obtained from the server after initializing the SSE channel
    private volatile String postUrl;
    private volatile McpPostEndpoint postEndpoint;
    private volatile McpOperationHandler operationHandler;
    private final McpClientAuthProvider mcpClientAuthProvider;
    private final McpHeadersSupplier headersSupplier;

    private volatile Runnable onFailure;
    private volatile boolean closed;

    public QuarkusHttpMcpTransport(QuarkusHttpMcpTransport.Builder builder) {
        sseUrl = ensureNotNull(builder.sseUrl, "Missing SSE endpoint URL");
        timeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        tlsConfiguration = builder.tlsConfiguration;

        this.logRequests = builder.logRequests;
        this.logResponses = builder.logResponses;

        QuarkusRestClientBuilder clientBuilder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(builder.sseUrl))
                .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .loggingScope(LoggingScope.ALL)
                .register(new JacksonBasicMessageBodyReader(QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER));
        if (tlsConfiguration != null) {
            clientBuilder.tlsConfiguration(tlsConfiguration);
        }

        if (builder.mcpClientAuthProvider != null) {
            this.mcpClientAuthProvider = builder.mcpClientAuthProvider;
        } else {
            this.mcpClientAuthProvider = McpClientAuthProvider.resolve(builder.mcpClientName).orElse(null);
        }
        if (mcpClientAuthProvider != null) {
            clientBuilder.register(new McpClientAuthFilter(mcpClientAuthProvider));
        }
        this.headersSupplier = getOrDefault(builder.headersSupplier, new McpHeadersSupplier() {
            @Override
            public Map<String, String> apply(McpCallContext i) {
                return Map.of();
            }
        });
        clientBuilder.register(new McpHeadersFilter(headersSupplier));
        if (logRequests || logResponses) {
            clientBuilder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            clientBuilder.clientLogger(new McpHttpClientLogger(logRequests, logResponses));
        }
        sseEndpoint = clientBuilder.build(McpSseEndpoint.class);
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        this.operationHandler = messageHandler;
        startSseChannel(logResponses);
        QuarkusRestClientBuilder builder = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(postUrl))
                .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .register(new JacksonBasicMessageBodyReader(QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER));
        if (mcpClientAuthProvider != null) {
            builder.register(new McpClientAuthFilter(mcpClientAuthProvider));
        }
        builder.register(new McpHeadersFilter(headersSupplier));
        if (logRequests || logResponses) {
            builder.loggingScope(LoggingScope.REQUEST_RESPONSE);
            builder.clientLogger(new McpHttpClientLogger(logRequests, logResponses));
        }
        if (tlsConfiguration != null) {
            builder.tlsConfiguration(tlsConfiguration);
        }

        postEndpoint = builder
                .build(McpPostEndpoint.class);
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest request) {
        return execute(request, request.getId()).onItem()
                .transformToUni(
                        response -> execute(new McpInitializationNotification(), null).onItem().transform(ignored -> response))
                .subscribeAsCompletionStage();
    }

    @Override
    public void checkHealth() {
        // no transport-specific checks right now
    }

    @Override
    public void onFailure(Runnable actionOnFailure) {
        this.onFailure = actionOnFailure;
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpClientMessage operation) {
        McpCallContext context = new McpCallContext(null, operation);
        return execute(context, operation.getId()).subscribeAsCompletionStage();
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpCallContext context) {
        return execute(context, context.message().getId()).subscribeAsCompletionStage();
    }

    @Override
    public void executeOperationWithoutResponse(McpClientMessage operation) {
        McpCallContext context = new McpCallContext(null, operation);
        execute(context, null).subscribe().with(ignored -> {
        });
    }

    @Override
    public void executeOperationWithoutResponse(McpCallContext context) {
        execute(context, null).subscribe().with(ignored -> {
        });
    }

    private Uni<JsonNode> execute(McpClientMessage message, Long id) {
        McpCallContext context = new McpCallContext(null, message);
        return execute(context, id);
    }

    private Uni<JsonNode> execute(McpCallContext context, Long id) {
        // NOTE: the id parameter is necessary because it will be null for responses to server-initiated operations
        // even though the ID inside the message itself will be set to an actual number, and in these cases
        // we don't want to register the operation in the operation handler
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        McpClientMessage request = context.message();
        Uni<JsonNode> uni = Uni.createFrom().completionStage(future);
        if (id != null) {
            operationHandler.startOperation(id, future);
        }
        postEndpoint.post(request)
                .onFailure().invoke(future::completeExceptionally)
                .onItem().invoke(response -> {
                    int statusCode = response.getStatus();
                    if (!isExpectedStatusCode(statusCode)) {
                        future.completeExceptionally(new RuntimeException("Unexpected status code: " + statusCode));
                    }
                    // For messages with null ID, we don't wait for a response in the SSE channel,
                    // so if the server accepted the request, we consider the operation done
                    if (id == null) {
                        future.complete(null);
                    }
                }).subscribeAsCompletionStage();
        return uni;
    }

    private boolean isExpectedStatusCode(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private void startSseChannel(boolean logResponses) {
        CompletableFuture<String> initializationFinished = new CompletableFuture<>();
        SseSubscriber listener = new SseSubscriber(operationHandler, logResponses, initializationFinished);
        sseEndpoint.get().subscribe().with(listener, throwable -> {
            if (!initializationFinished.isDone()) {
                log.warn("Failed to connect to the SSE channel, the MCP client will not be used", throwable);
                initializationFinished.completeExceptionally(throwable);
            }
            if (!closed) {
                onFailure.run();
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
        closed = true;
    }

    public static class Builder {

        private String sseUrl;
        private String mcpClientName;
        private Duration timeout;
        private boolean logRequests = false;
        private boolean logResponses = false;
        private TlsConfiguration tlsConfiguration;
        private McpClientAuthProvider mcpClientAuthProvider;
        private McpHeadersSupplier headersSupplier;

        /**
         * The initial URL where to connect to the server and request a SSE
         * channel.
         */
        public QuarkusHttpMcpTransport.Builder sseUrl(String sseUrl) {
            this.sseUrl = sseUrl;
            return this;
        }

        public QuarkusHttpMcpTransport.Builder mcpClientName(String mcpClientName) {
            this.mcpClientName = mcpClientName;
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

        public QuarkusHttpMcpTransport.Builder tlsConfiguration(TlsConfiguration tlsConfiguration) {
            this.tlsConfiguration = tlsConfiguration;
            return this;
        }

        public QuarkusHttpMcpTransport.Builder mcpClientAuthProvider(McpClientAuthProvider mcpClientAuthProvider) {
            this.mcpClientAuthProvider = mcpClientAuthProvider;
            return this;
        }

        public QuarkusHttpMcpTransport.Builder headers(Map<String, String> headers) {
            this.headersSupplier = new McpHeadersSupplier() {
                @Override
                public Map<String, String> apply(McpCallContext i) {
                    return headers;
                }
            };
            return this;
        }

        public QuarkusHttpMcpTransport.Builder headers(Supplier<Map<String, String>> headersSupplier) {
            this.headersSupplier = new McpHeadersSupplier() {
                @Override
                public Map<String, String> apply(McpCallContext i) {
                    return headersSupplier.get();
                }
            };
            return this;
        }

        public QuarkusHttpMcpTransport build() {
            return new QuarkusHttpMcpTransport(this);
        }
    }

}
