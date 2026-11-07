package io.quarkiverse.langchain4j.mcp.test;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public abstract class AbstractMockHttpMcpServer {

    private final AtomicLong ID_GENERATOR = new AtomicLong(new Random().nextLong(1000, 5000));

    private static Logger logger = Logger.getLogger(AbstractMockHttpMcpServer.class);

    private volatile boolean shouldRespondToPing = true;

    // key = operation ID of the ping
    // value = future that will be completed when the ping response for that ID is received
    final Map<Long, CompletableFuture<Void>> pendingPings = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean initializationNotificationReceived = false;

    @Inject
    ScheduledExecutorService scheduledExecutorService;

    protected abstract String getEndpoint();

    @Path("/mcp")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @POST
    public Response post(@HeaderParam("Authorization") String authorization, JsonNode message) {
        if (!verifyAuthorization(authorization)) {
            return Response.status(401).build();
        }
        if (message.get("method") != null) {
            String method = message.get("method").asText();
            if (method.equals("notifications/cancelled")) {
                return Response.accepted().build();
            }
            if (method.equals("notifications/initialized")) {
                if (initializationNotificationReceived) {
                    return Response.serverError().entity("Duplicate 'notifications/initialized' message").build();
                }
                initializationNotificationReceived = true;
                return Response.accepted().build();
            }
            String operationId = message.get("id").asText();
            if (method.equals("initialize")) {
                return Response.ok(initialize(operationId)).build();
            } else if (method.equals("tools/list")) {
                return Response.ok(listTools(operationId)).build();
            } else if (method.equals("tools/call")) {
                if (message.get("params").get("name").asText().equals("add")) {
                    return Response.ok(executeAddOperation(message, operationId)).build();
                } else if (message.get("params").get("name").asText().equals("logging")) {
                    return Response.ok(executeLoggingOperation(message, operationId)).build();
                } else if (message.get("params").get("name").asText().equals("longRunningOperation")) {
                    return executeLongRunningOperation(message, operationId);
                } else {
                    return Response.serverError().entity("Unknown operation").build();
                }
            } else if (method.equals("ping")) {
                if (shouldRespondToPing) {
                    ObjectNode result = buildPongMessage(operationId);
                    return Response.ok(result).build();
                } else {
                    logger.info("Ignoring ping request");
                    return Response.accepted().build();
                }
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

    private ObjectNode executeLoggingOperation(JsonNode message, String operationId) {
        // Note: In streamable HTTP, we can't send server-initiated notifications like logs
        // during the request. For simplicity, we just return the tool result.
        // In a real implementation, logs would need to be handled differently.
        ObjectNode result = buildToolResult(operationId, "OK");
        return result;
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

    private ObjectNode listTools(String operationId) {
        try {
            String response = getToolsListResponse().formatted(operationId);
            return objectMapper.readValue(response, ObjectNode.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tools list response", e);
        }
    }

    protected abstract String getToolsListResponse();

    private ObjectNode initialize(String operationId) {
        ObjectNode initializeResponse = objectMapper.createObjectNode();
        initializeResponse
                .put("id", operationId)
                .put("jsonrpc", "2.0")
                .putObject("result")
                .put("protocolVersion", "2024-11-05");
        return initializeResponse;
    }

    private ObjectNode executeAddOperation(JsonNode message, String operationId) {
        int a = message.get("params").get("arguments").get("a").asInt();
        int b = message.get("params").get("arguments").get("b").asInt();
        int additionResult = a + b;
        ObjectNode result = buildToolResult(operationId, "The sum of " + a + " and " + b + " is " + additionResult + ".");
        return result;
    }

    private Response executeLongRunningOperation(JsonNode message, String operationId) {
        int duration = message.get("params").get("arguments").get("duration").asInt();
        try {
            // Block the request thread to simulate a long-running operation
            Thread.sleep(duration * 1000L);
            ObjectNode result = buildToolResult(operationId, "Operation completed.");
            return Response.ok(result).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.serverError().entity("Operation interrupted").build();
        }
    }

    long sendPing() {
        // Note: Server-initiated pings are not supported in streamable HTTP transport
        // the same way they were in SSE. We keep this method for backward compatibility
        // with tests, but it doesn't actually send a ping anymore.
        long id = ID_GENERATOR.incrementAndGet();
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.complete(null); // Complete immediately since we can't send server-initiated pings
        pendingPings.put(id, future);
        return id;
    }

    void stopRespondingToPings() {
        shouldRespondToPing = false;
    }

    protected boolean verifyAuthorization(String authorization) {
        return true;
    }
}
