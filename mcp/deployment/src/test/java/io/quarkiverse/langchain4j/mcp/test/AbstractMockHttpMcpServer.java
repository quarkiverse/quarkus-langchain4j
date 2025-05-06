package io.quarkiverse.langchain4j.mcp.test;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public abstract class AbstractMockHttpMcpServer {

    private final AtomicLong ID_GENERATOR = new AtomicLong(new Random().nextLong(1000, 5000));

    private static Logger logger = Logger.getLogger(MockHttpMcpServer.class);

    private volatile boolean shouldRespondToPing = true;

    // key = operation ID of the ping
    // value = future that will be completed when the ping response for that ID is received
    final Map<Long, CompletableFuture<Void>> pendingPings = new ConcurrentHashMap<>();

    private volatile SseEventSink sink;
    private volatile Sse sse;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean initializationNotificationReceived = false;

    @Inject
    ScheduledExecutorService scheduledExecutorService;

    @Path("/sse")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public void sse(@Context SseEventSink sink, @Context Sse sse) {
        this.sink = sink;
        this.sse = sse;
        sink.send(sse.newEventBuilder()
                .id("id")
                .name("endpoint")
                .mediaType(MediaType.TEXT_PLAIN_TYPE)
                .data("/" + getEndpoint() + "/post")
                .build());
    }

    protected abstract String getEndpoint();

    @Path("/post")
    @Consumes(MediaType.APPLICATION_JSON)
    @POST
    public Response post(JsonNode message) {
        if (message.get("method") != null) {
            String method = message.get("method").asText();
            if (method.equals("notifications/cancelled")) {
                return Response.ok().build();
            }
            if (method.equals("notifications/initialized")) {
                if (initializationNotificationReceived) {
                    return Response.serverError().entity("Duplicate 'notifications/initialized' message").build();
                }
                initializationNotificationReceived = true;
                return Response.ok().build();
            }
            String operationId = message.get("id").asText();
            if (method.equals("initialize")) {
                initialize(operationId);
            } else if (method.equals("tools/list")) {
                ensureInitialized();
                listTools(operationId);
            } else if (method.equals("tools/call")) {
                ensureInitialized();
                if (message.get("params").get("name").asText().equals("add")) {
                    executeAddOperation(message, operationId);
                } else if (message.get("params").get("name").asText().equals("logging")) {
                    executeLoggingOperation(message, operationId);
                } else if (message.get("params").get("name").asText().equals("longRunningOperation")) {
                    executeLongRunningOperation(message, operationId);
                } else {
                    return Response.serverError().entity("Unknown operation").build();
                }

            } else if (method.equals("ping")) {
                if (shouldRespondToPing) {
                    ObjectNode result = buildPongMessage(operationId);
                    sink.send(sse.newEventBuilder()
                            .name("message")
                            .data(result)
                            .build());
                } else {
                    logger.info("Ignoring ping request");
                }
                return Response.accepted().build();
            }
        } else {
            // if 'method' is null, the message is probably a ping response
            long id = message.get("id").asLong();
            CompletableFuture<Void> future = pendingPings.remove(id);
            if (future != null) {
                future.complete(null);
            } else {
                return Response.serverError().entity("Received a ping response with unknown ID " + id).build();
            }
        }
        return Response.accepted().build();
    }

    private ObjectNode buildPongMessage(String operationId) {
        ObjectNode pong = objectMapper.createObjectNode();
        pong.put("jsonrpc", "2.0");
        pong.put("id", operationId);
        pong.put("result", objectMapper.createObjectNode());
        return pong;
    }

    private void executeLoggingOperation(JsonNode message, String operationId) {
        ObjectNode logData = objectMapper.createObjectNode();
        logData.put("message", "This is a log message");
        ObjectNode log = buildLoggingMessage(logData);
        sink.send(sse.newEventBuilder()
                .name("message")
                .data(log)
                .build());
        ObjectNode result = buildToolResult(operationId, "OK");
        sink.send(sse.newEventBuilder()
                .name("message")
                .data(result)
                .build());
    }

    private ObjectNode buildLoggingMessage(JsonNode message) {
        ObjectNode log = objectMapper.createObjectNode();
        log.put("jsonrpc", "2.0");
        log.put("method", "notifications/message");
        ObjectNode params = objectMapper.createObjectNode();
        log.set("params", params);
        params.put("level", "info");
        params.put("logger", getEndpoint());
        params.set("data", message);
        return log;
    }

    private ObjectNode buildToolResult(String operationId, String result) {
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("id", operationId);
        resultNode.put("jsonrpc", "2.0");
        ObjectNode resultContent = objectMapper.createObjectNode();
        resultNode.set("result", resultContent);
        resultContent.putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", result);
        return resultNode;
    }

    // throw an exception if we haven't received the 'notifications/initialized' message yet
    private void ensureInitialized() {
        if (!initializationNotificationReceived) {
            throw new IllegalStateException("The client has not sent the 'notifications/initialized' message yet");
        }
    }

    private void listTools(String operationId) {
        String response = getToolsListResponse().formatted(operationId);
        sink.send(sse.newEventBuilder()
                .name("message")
                .data(response)
                .build());
    }

    protected abstract String getToolsListResponse();

    private void initialize(String operationId) {
        ObjectNode initializeResponse = objectMapper.createObjectNode();
        initializeResponse
                .put("id", operationId)
                .put("jsonrpc", "2.0")
                .putObject("result")
                .put("protocolVersion", "2024-11-05");
        sink.send(sse.newEventBuilder()
                .name("message")
                .data(initializeResponse)
                .build());
    }

    private void executeAddOperation(JsonNode message, String operationId) {
        int a = message.get("params").get("arguments").get("a").asInt();
        int b = message.get("params").get("arguments").get("b").asInt();
        int additionResult = a + b;
        ObjectNode result = buildToolResult(operationId, "The sum of " + a + " and " + b + " is " + additionResult + ".");
        sink.send(sse.newEventBuilder()
                .name("message")
                .data(result)
                .build());
    }

    private void executeLongRunningOperation(JsonNode message, String operationId) {
        int duration = message.get("params").get("arguments").get("duration").asInt();
        scheduledExecutorService.schedule(() -> {
            ObjectNode result = buildToolResult(operationId, "Operation completed.");
            sink.send(sse.newEventBuilder()
                    .name("message")
                    .data(result)
                    .build());
        }, duration, TimeUnit.SECONDS);
    }

    long sendPing() {
        ObjectNode initializeResponse = objectMapper.createObjectNode();
        long id = ID_GENERATOR.incrementAndGet();
        initializeResponse
                .put("id", id)
                .put("jsonrpc", "2.0")
                .put("method", "ping");
        sink.send(sse.newEventBuilder()
                .name("message")
                .data(initializeResponse)
                .build());
        pendingPings.put(id, new CompletableFuture<>());
        return id;
    }

    void stopRespondingToPings() {
        shouldRespondToPing = false;
    }
}
