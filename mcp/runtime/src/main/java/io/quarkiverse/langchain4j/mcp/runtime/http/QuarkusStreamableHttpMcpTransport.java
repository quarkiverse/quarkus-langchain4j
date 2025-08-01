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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.MultivaluedMap;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.client.SseEvent;
import org.jboss.resteasy.reactive.common.util.MultivaluedTreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.mcp.client.protocol.InitializationNotification;
import dev.langchain4j.mcp.client.protocol.McpClientMessage;
import dev.langchain4j.mcp.client.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.parsetools.RecordParser;

public class QuarkusStreamableHttpMcpTransport implements McpTransport {

    private static final Logger log = Logger.getLogger(QuarkusStreamableHttpMcpTransport.class);
    private final String url;
    private final Duration timeout;
    private final boolean logResponses;
    private final boolean logRequests;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> mcpSessionId = new AtomicReference<>();
    private volatile McpOperationHandler operationHandler;
    private final McpClientAuthProvider mcpClientAuthProvider;
    private final HttpClient httpClient;
    private volatile SseSubscriber sseSubscriber;

    private volatile Runnable onFailure;
    private volatile boolean closed;

    public QuarkusStreamableHttpMcpTransport(QuarkusStreamableHttpMcpTransport.Builder builder) {
        this.url = ensureNotNull(builder.url, "Missing MCP endpoint URL");
        this.timeout = getOrDefault(builder.timeout, Duration.ofSeconds(60));
        this.httpClient = builder.httpClient;
        this.logRequests = builder.logRequests;
        this.logResponses = builder.logResponses;
        this.mcpClientAuthProvider = McpClientAuthProvider.resolve(builder.mcpClientName).orElse(null);
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        this.operationHandler = messageHandler;
        this.sseSubscriber = new SseSubscriber(operationHandler, logResponses, null);
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest request) {
        return execute(request, request.getId()).onItem()
                .transformToUni(
                        response -> execute(new InitializationNotification(), null).onItem().transform(ignored -> response))
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
        return execute(operation, operation.getId()).subscribeAsCompletionStage();
    }

    @Override
    public void executeOperationWithoutResponse(McpClientMessage operation) {
        execute(operation, null).subscribe().with(ignored -> {
        });
    }

    private Uni<JsonNode> execute(McpClientMessage request, Long id) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        Uni<JsonNode> uni = Uni.createFrom().completionStage(future);
        if (id != null) {
            operationHandler.startOperation(id, future);
        }
        String body = null;
        try {
            body = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            future.completeExceptionally(e);
        }
        if (logRequests) {
            log.info("Request: " + body);
        }
        RequestOptions options = new RequestOptions()
                .setAbsoluteURI(url)
                .addHeader("Accept", "application/json,text/event-stream")
                .setMethod(HttpMethod.POST);
        if (mcpSessionId.get() != null) {
            options.addHeader("Mcp-Session-Id", mcpSessionId.get());
        }
        if (mcpClientAuthProvider != null) {
            String authValue = mcpClientAuthProvider.getAuthorization(new McpClientAuthFilter.AuthInputImpl("POST",
                    URI.create(url), toMultivaluedMap(options.getHeaders())));
            if (authValue != null) {
                options.addHeader("Authorization", authValue);
            }
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

                                    RecordParser sseEventparser = RecordParser.newDelimited("\n\n", bodyBuffer -> {
                                        String responseString = bodyBuffer.toString();
                                        SseEvent<String> sseEvent = parseSseEvent(responseString);
                                        sseSubscriber.accept(sseEvent);
                                    });

                                    String contentType = response.result().getHeader("Content-Type");
                                    if (id != null && contentType != null && contentType.contains("text/event-stream")) {
                                        // the server has started a SSE channel
                                        response.result().handler(sseEventparser);
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
                                } else {
                                    future.completeExceptionally(
                                            new RuntimeException("Unexpected status code: " + response.result().statusCode()));
                                }
                            }
                        });
                    }
                });
        return uni;
    }

    private MultivaluedMap<String, Object> toMultivaluedMap(MultiMap multiMap) {
        MultivaluedTreeMap<String, Object> map = new MultivaluedTreeMap<>();
        multiMap.forEach((key, value) -> map.add(key, value));
        return map;
    }

    // FIXME: this may be brittle, is there a more standard way to parse SSE events?
    private SseEvent<String> parseSseEvent(String responseString) {
        Map<String, String> entries = Arrays.stream(responseString.split("\\n"))
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
        closed = true;
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

        public QuarkusStreamableHttpMcpTransport build() {
            return new QuarkusStreamableHttpMcpTransport(this);
        }
    }

}
