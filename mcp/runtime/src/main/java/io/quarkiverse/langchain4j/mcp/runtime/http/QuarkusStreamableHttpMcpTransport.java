package io.quarkiverse.langchain4j.mcp.runtime.http;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.MultivaluedMap;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.SseEvent;
import org.jboss.resteasy.reactive.common.util.MultivaluedTreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.McpHeadersSupplier;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import io.quarkiverse.langchain4j.mcp.auth.McpAuthenticationException;
import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;

public class QuarkusStreamableHttpMcpTransport implements McpTransport {

    private static final Logger log = Logger.getLogger(QuarkusStreamableHttpMcpTransport.class);
    private static final long DEFAULT_SUBSIDIARY_RETRY_MS = 5000;
    private final String url;
    private final Duration timeout;
    private final boolean logResponses;
    private final boolean logRequests;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> mcpSessionId = new AtomicReference<>();
    private volatile McpOperationHandler operationHandler;
    private final McpClientAuthProvider mcpClientAuthProvider;
    private final McpHeadersSupplier headersSupplier;
    private final HttpClient httpClient;
    private McpInitializeRequest initializeRequest;
    private volatile SseSubscriber sseSubscriber;

    private volatile Runnable onFailure;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Subsidiary SSE channel fields
    private final boolean subsidiaryChannelEnabled;
    private volatile boolean subsidiaryChannelEstablished;
    private final AtomicReference<String> subsidiaryLastEventId = new AtomicReference<>();
    private final AtomicLong subsidiaryRetryMs = new AtomicLong(DEFAULT_SUBSIDIARY_RETRY_MS);

    public QuarkusStreamableHttpMcpTransport(QuarkusStreamableHttpMcpTransport.Builder builder) {
        this.url = ensureNotNull(builder.url, "Missing MCP endpoint URL");
        this.timeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        this.httpClient = builder.httpClient;
        this.logRequests = builder.logRequests;
        this.logResponses = builder.logResponses;
        this.subsidiaryChannelEnabled = builder.subsidiaryChannelEnabled;
        if (builder.mcpClientAuthProvider != null) {
            this.mcpClientAuthProvider = builder.mcpClientAuthProvider;
        } else {
            this.mcpClientAuthProvider = McpClientAuthProvider.resolve(builder.mcpClientName).orElse(null);
        }
        this.headersSupplier = getOrDefault(builder.headersSupplier, new McpHeadersSupplier() {
            @Override
            public Map<String, String> apply(McpCallContext i) {
                return Map.of();
            }
        });
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        this.operationHandler = messageHandler;
        this.sseSubscriber = new SseSubscriber(operationHandler, logResponses, null);
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest request) {
        this.initializeRequest = request;
        McpCallContext ctx = new McpCallContext(null, request);
        return execute(ctx, false, true)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .onItem()
                .transformToUni(
                        response -> execute(new McpInitializationNotification(), false, true).onItem()
                                .transform(ignored -> response))
                .subscribeAsCompletionStage()
                .thenCompose(originalResponse -> {
                    if (subsidiaryChannelEnabled) {
                        return startSubsidiaryChannel(true)
                                .thenCompose(v -> CompletableFuture.completedFuture(originalResponse));
                    }
                    return CompletableFuture.completedFuture(originalResponse);
                });
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
        return execute(context, false, true).subscribeAsCompletionStage();
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpCallContext context) {
        return execute(context, false, true).subscribeAsCompletionStage();
    }

    @Override
    public void executeOperationWithoutResponse(McpClientMessage operation) {
        execute(new McpCallContext(null, operation), false, false).subscribe().with(ignored -> {
        });
    }

    @Override
    public void executeOperationWithoutResponse(McpCallContext context) {
        execute(context, false, false).subscribe().with(ignored -> {
        });
    }

    private Uni<JsonNode> execute(McpClientMessage operation, boolean isRetry, boolean expectsResponse) {
        return execute(new McpCallContext(null, operation), isRetry, expectsResponse);
    }

    private Uni<JsonNode> execute(McpCallContext context, boolean isRetry, boolean expectsResponse) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        Uni<JsonNode> uni = Uni.createFrom().completionStage(future);
        Long id = context.message().getId();
        McpClientMessage request = context.message();
        if (expectsResponse && id != null) {
            operationHandler.startOperation(id, future);
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            future.completeExceptionally(e);
            return uni;
        }
        if (logRequests) {
            log.info("Request: " + body);
        }
        RequestOptions options = new RequestOptions()
                .setAbsoluteURI(url)
                .addHeader("Accept", "application/json,text/event-stream")
                .addHeader("Content-Type", "application/json")
                .setMethod(HttpMethod.POST);
        if (mcpSessionId.get() != null && !(request instanceof McpInitializeRequest)) {
            options.addHeader("Mcp-Session-Id", mcpSessionId.get());
        }
        if (mcpClientAuthProvider != null) {
            String authValue = mcpClientAuthProvider.getAuthorization(new McpClientAuthFilter.AuthInputImpl("POST",
                    URI.create(url), toMultivaluedMap(options.getHeaders())));
            if (authValue != null) {
                options.addHeader("Authorization", authValue);
            }
        }
        Map<String, String> customHeaders = headersSupplier.apply(context);
        if (customHeaders != null) {
            customHeaders.forEach((name, value) -> options.addHeader(name, value));
        }
        String finalBody = body;
        httpClient.request(options)
                .onComplete(result -> {
                    if (result.failed()) {
                        future.completeExceptionally(result.cause());
                    } else {
                        result.result().send(finalBody).onComplete(response -> {
                            if (response.failed()) {
                                future.completeExceptionally(response.cause());
                            } else {
                                if (isExpectedStatusCode(response.result().statusCode())) {
                                    // did the server assign a session ID?
                                    String mcpSessionId = response.result().getHeader("Mcp-Session-Id");
                                    if (mcpSessionId != null && !mcpSessionId.isEmpty()) {
                                        log.debug("Assigned MCP session ID: " + mcpSessionId);
                                        this.mcpSessionId.set(mcpSessionId);
                                    }
                                    String contentType = response.result().getHeader("Content-Type");
                                    if (id != null && contentType != null && contentType.contains("text/event-stream")) {
                                        // the server has started an SSE channel
                                        response.result().handler(createSseBufferHandler(eventStr -> {
                                            SseEvent<String> sseEvent = parseSseEvent(eventStr);
                                            sseSubscriber.accept(sseEvent);
                                        }));
                                    } else {
                                        // the server has sent a single regular response
                                        if (id == null) {
                                            // For operations with null ID, we don't expect
                                            // a response, just mark the operation done when
                                            // the server accepted it
                                            future.complete(null);
                                        }
                                        response.result().bodyHandler(bodyBuffer -> {
                                            try {
                                                String responseString = bodyBuffer.toString();
                                                JsonNode node = objectMapper.readTree(responseString);
                                                if (logResponses) {
                                                    log.info("Response: " + responseString);
                                                }
                                                operationHandler.handle(node);
                                            } catch (JsonProcessingException e) {
                                                future.completeExceptionally(e);
                                            }
                                        });
                                    }
                                } else if (!(request instanceof McpInitializeRequest) && response.result().statusCode() == 404
                                        && !isRetry) {
                                    log.debug("Received 404 for operation, retrying after re-initialize");
                                    McpInitializeRequest initReq = QuarkusStreamableHttpMcpTransport.this.initializeRequest;
                                    if (initReq == null) {
                                        future.completeExceptionally(
                                                new IllegalStateException("Cannot retry 404: initializeRequest is null"));
                                        return;
                                    }
                                    initialize(initReq).thenAccept(node -> {
                                        execute(request, true, true)
                                                .subscribeAsCompletionStage()
                                                .thenAccept(future::complete)
                                                .exceptionally(t -> {
                                                    future.completeExceptionally(t);
                                                    return null;
                                                });
                                    })
                                            .exceptionally(t -> {
                                                future.completeExceptionally(t);
                                                return null;
                                            });
                                } else {
                                    int statusCode = response.result().statusCode();
                                    if (statusCode == 401) {
                                        String wwwAuth = response.result().getHeader("WWW-Authenticate");
                                        future.completeExceptionally(new McpAuthenticationException(statusCode, wwwAuth));
                                    } else {
                                        response.result().bodyHandler(bodyBuffer -> {
                                            String responseString = bodyBuffer.toString();
                                            future.completeExceptionally(
                                                    new RuntimeException(
                                                            "Unexpected status code: " + response.result().statusCode()
                                                                    + ", body: " + responseString));
                                        });
                                    }
                                }
                            }
                        });
                    }
                });
        return uni;
    }

    /**
     * Opens the subsidiary SSE channel by issuing an HTTP GET to the MCP endpoint.
     * This allows the server to send notifications and requests to the client
     * without the client first sending data via HTTP POST.
     *
     * @param firstAttempt if true, failures will not trigger reconnection
     * @return a future that completes when the channel setup attempt finishes
     */
    private CompletableFuture<Void> startSubsidiaryChannel(boolean firstAttempt) {
        if (closed.get()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        RequestOptions options = new RequestOptions()
                .setAbsoluteURI(url)
                .addHeader("Accept", "text/event-stream")
                .setMethod(HttpMethod.GET);
        String sessionId = mcpSessionId.get();
        if (sessionId != null) {
            options.addHeader("Mcp-Session-Id", sessionId);
        }
        String lastId = subsidiaryLastEventId.get();
        if (lastId != null) {
            options.addHeader("Last-Event-ID", lastId);
        }
        if (mcpClientAuthProvider != null) {
            String authValue = mcpClientAuthProvider.getAuthorization(new McpClientAuthFilter.AuthInputImpl("GET",
                    URI.create(url), toMultivaluedMap(options.getHeaders())));
            if (authValue != null) {
                options.addHeader("Authorization", authValue);
            }
        }
        Map<String, String> customHeaders = headersSupplier.apply(null);
        if (customHeaders != null) {
            customHeaders.forEach((name, value) -> options.addHeader(name, value));
        }

        httpClient.request(options)
                .onComplete(requestResult -> {
                    if (requestResult.failed()) {
                        if (!closed.get()) {
                            if (firstAttempt) {
                                log.warn("Failed to open subsidiary SSE channel", requestResult.cause());
                            } else {
                                log.debug("Subsidiary SSE channel connection failed, scheduling retry",
                                        requestResult.cause());
                                scheduleSubsidiaryReconnect();
                            }
                        }
                        result.complete(null);
                        return;
                    }
                    requestResult.result().send().onComplete(response -> {
                        if (response.failed()) {
                            if (!closed.get()) {
                                if (firstAttempt) {
                                    log.warn("Failed to open subsidiary SSE channel", response.cause());
                                } else {
                                    log.debug("Subsidiary SSE channel connection failed, scheduling retry",
                                            response.cause());
                                    scheduleSubsidiaryReconnect();
                                }
                            }
                            result.complete(null);
                            return;
                        }
                        int statusCode = response.result().statusCode();
                        String contentType = response.result().getHeader("Content-Type");
                        if (isExpectedStatusCode(statusCode)
                                && contentType != null
                                && contentType.contains("text/event-stream")) {
                            subsidiaryChannelEstablished = true;
                            log.debug("Subsidiary SSE channel established");
                            result.complete(null);

                            response.result().handler(
                                    createSseBufferHandler(this::processSubsidiarySseEvent));
                            response.result().endHandler(v -> {
                                log.debug("Subsidiary SSE channel closed");
                                if (!closed.get()) {
                                    scheduleSubsidiaryReconnect();
                                }
                            });
                            response.result().exceptionHandler(t -> {
                                log.debug("Subsidiary SSE channel error", t);
                                if (!closed.get()) {
                                    scheduleSubsidiaryReconnect();
                                }
                            });
                        } else {
                            if (firstAttempt) {
                                log.warnf(
                                        "Failed to open subsidiary SSE channel (status=%d, contentType=%s), will not re-attempt",
                                        statusCode,
                                        contentType != null ? contentType : "absent");
                            } else {
                                log.debugf(
                                        "Failed to reconnect subsidiary SSE channel (status=%d, contentType=%s), scheduling retry",
                                        statusCode,
                                        contentType != null ? contentType : "absent");
                                if (!closed.get()) {
                                    scheduleSubsidiaryReconnect();
                                }
                            }
                            result.complete(null);
                        }
                    });
                });
        return result;
    }

    /**
     * Processes an SSE event received on the subsidiary channel.
     * Handles data, id, and retry fields.
     */
    private void processSubsidiarySseEvent(String eventStr) {
        // Parse SSE fields from the raw event string
        String[] lines = eventStr.split("\\R");
        for (String line : lines) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if (logResponses) {
                    log.info("Subsidiary SSE event received: " + data);
                }
                try {
                    JsonNode jsonNode = objectMapper.readTree(data);
                    operationHandler.handle(jsonNode);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse subsidiary SSE event: " + data, e);
                }
            } else if (line.startsWith("id:")) {
                subsidiaryLastEventId.set(line.substring(3).trim());
            } else if (line.startsWith("retry:")) {
                try {
                    subsidiaryRetryMs.set(Long.parseLong(line.substring(6).trim()));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse SSE retry value: " + line, e);
                }
            }
        }
    }

    private void scheduleSubsidiaryReconnect() {
        if (closed.get() || !subsidiaryChannelEstablished) {
            return;
        }
        long delayMs = subsidiaryRetryMs.get();
        log.debugf("Scheduling subsidiary SSE channel reconnect in %d ms", delayMs);
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (!closed.get()) {
                        startSubsidiaryChannel(false);
                    }
                });
    }

    /**
     * Creates a Handler that buffers incoming data and splits it into
     * individual SSE events separated by {@code \r\n\r\n} or {@code \n\n},
     * passing each complete raw event string to the provided consumer.
     */
    private Handler<Buffer> createSseBufferHandler(Consumer<String> eventConsumer) {
        return new Handler<>() {
            private StringBuffer sb = new StringBuffer();

            @Override
            public void handle(Buffer event) {
                sb.append(event.toString());
                String str = sb.toString();
                while (true) {
                    int sepIdx;
                    int sepLen;
                    int crlfIdx = str.indexOf("\r\n\r\n");
                    int lfIdx = str.indexOf("\n\n");
                    if (crlfIdx >= 0 && (lfIdx < 0 || crlfIdx <= lfIdx)) {
                        sepIdx = crlfIdx;
                        sepLen = 4;
                    } else if (lfIdx >= 0) {
                        sepIdx = lfIdx;
                        sepLen = 2;
                    } else {
                        break;
                    }
                    String eventStr = str.substring(0, sepIdx);
                    str = str.substring(sepIdx + sepLen);
                    sb = new StringBuffer(str);
                    eventConsumer.accept(eventStr);
                }
            }
        };
    }

    private MultivaluedMap<String, Object> toMultivaluedMap(MultiMap multiMap) {
        MultivaluedTreeMap<String, Object> map = new MultivaluedTreeMap<>();
        multiMap.forEach((key, value) -> map.add(key, value));
        return map;
    }

    // FIXME: this may be brittle, is there a more standard way to parse SSE events?
    private SseEvent<String> parseSseEvent(String responseString) {
        // use \\R to match any line ending because some servers use \r\n and some use \n
        Map<String, String> entries = Arrays.stream(responseString.split("\\R"))
                .collect(Collectors.toMap(s -> s.substring(0, s.indexOf(":")),
                        s -> s.substring(s.indexOf(":") + 2)));
        return new SseEvent<String>() {
            @Override
            public String id() {
                return entries.get("id");
            }

            @Override
            public String name() {
                return entries.get("event");
            }

            @Override
            public String comment() {
                return null;
            }

            @Override
            public String data() {
                return entries.get("data");
            }
        };
    }

    private boolean isExpectedStatusCode(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public void close() throws IOException {
        closed.set(true);
        try {
            httpClient.close().toCompletionStage().toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Builder {

        private String url;
        private String mcpClientName;
        private Duration timeout;
        private boolean logRequests = false;
        private boolean logResponses = false;
        private HttpClient httpClient;
        private McpClientAuthProvider mcpClientAuthProvider;
        private McpHeadersSupplier headersSupplier;
        private boolean subsidiaryChannelEnabled = false;

        /**
         * The initial URL where to connect to the server and request a SSE
         * channel.
         */
        public QuarkusStreamableHttpMcpTransport.Builder url(String url) {
            this.url = url;
            return this;
        }

        public QuarkusStreamableHttpMcpTransport.Builder mcpClientName(String mcpClientName) {
            this.mcpClientName = mcpClientName;
            return this;
        }

        public QuarkusStreamableHttpMcpTransport.Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public QuarkusStreamableHttpMcpTransport.Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public QuarkusStreamableHttpMcpTransport.Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder mcpClientAuthProvider(McpClientAuthProvider mcpClientAuthProvider) {
            this.mcpClientAuthProvider = mcpClientAuthProvider;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headersSupplier = new McpHeadersSupplier() {
                @Override
                public Map<String, String> apply(McpCallContext i) {
                    return headers;
                }
            };
            return this;
        }

        public Builder headers(Supplier<Map<String, String>> headersSupplier) {
            this.headersSupplier = new McpHeadersSupplier() {
                @Override
                public Map<String, String> apply(McpCallContext i) {
                    return headersSupplier.get();
                }
            };
            return this;
        }

        public Builder headers(McpHeadersSupplier headersSupplier) {
            this.headersSupplier = headersSupplier;
            return this;
        }

        /**
         * Enables or disables the subsidiary SSE channel. When enabled, the transport
         * will open an HTTP GET-based SSE stream after initialization, allowing the
         * server to send notifications and requests to the client without the client
         * first sending data via HTTP POST.
         * Defaults to {@code false}.
         */
        public Builder subsidiaryChannel(boolean subsidiaryChannelEnabled) {
            this.subsidiaryChannelEnabled = subsidiaryChannelEnabled;
            return this;
        }

        public QuarkusStreamableHttpMcpTransport build() {
            return new QuarkusStreamableHttpMcpTransport(this);
        }
    }

}
