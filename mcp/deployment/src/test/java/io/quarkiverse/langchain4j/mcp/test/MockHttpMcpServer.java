package io.quarkiverse.langchain4j.mcp.test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

import org.jboss.resteasy.reactive.RestStreamElementType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A very basic mock MCP server using the HTTP transport.
 */
@Path("/mock-mcp")
public class MockHttpMcpServer {

    // language=JSON
    public static final String TOOLS_LIST_RESPONSE = """
            {
              "result": {
                "tools": [
                  {
                    "name": "longRunningOperation",
                    "description": "Demonstrates a long running operation with progress updates",
                    "inputSchema": {
                      "type": "object",
                      "properties": {
                        "duration": {
                          "type": "number",
                          "default": 10,
                          "description": "Duration of the operation in seconds"
                        },
                        "steps": {
                          "type": "number",
                          "default": 5,
                          "description": "Number of steps in the operation"
                        }
                      },
                      "additionalProperties": false,
                      "$schema": "http://json-schema.org/draft-07/schema#"
                    }
                  },
                  {
                    "name": "add",
                    "description": "Adds two numbers",
                    "inputSchema": {
                      "type": "object",
                      "properties": {
                        "a": {
                          "type": "number",
                          "description": "First number"
                        },
                        "b": {
                          "type": "number",
                          "description": "Second number"
                        }
                      },
                      "required": [
                        "a",
                        "b"
                      ],
                      "additionalProperties": false,
                      "$schema": "http://json-schema.org/draft-07/schema#"
                    }
                  }
                ]
              },
              "jsonrpc": "2.0",
              "id": "%s"
            }
            """;

    private volatile SseEventSink sink;
    private volatile Sse sse;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                .data("/mock-mcp/post")
                .build());
    }

    @Path("/post")
    @Consumes(MediaType.APPLICATION_JSON)
    @POST
    public Response post(JsonNode message) {
        String method = message.get("method").asText();
        if (method.equals("notifications/cancelled")) {
            return Response.ok().build();
        }
        String operationId = message.get("id").asText();
        if (method.equals("initialize")) {
            initialize(operationId);
        } else if (method.equals("tools/list")) {
            listTools(operationId);
        } else if (method.equals("tools/call")) {
            if (message.get("params").get("name").asText().equals("add")) {
                executeAddOperation(message, operationId);
            } else if (message.get("params").get("name").asText().equals("longRunningOperation")) {
                executeLongRunningOperation(message, operationId);
            } else {
                return Response.serverError().entity("Unknown operation").build();
            }
        }
        return Response.accepted().build();
    }

    private void listTools(String operationId) {
        String response = TOOLS_LIST_RESPONSE.formatted(operationId);
        sink.send(sse.newEventBuilder()
                .name("message")
                .data(response)
                .build());
    }

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
        ObjectNode result = objectMapper.createObjectNode();
        result.put("id", operationId);
        result.put("jsonrpc", "2.0");
        ObjectNode resultContent = objectMapper.createObjectNode();
        result.set("result", resultContent);
        int a = message.get("params").get("arguments").get("a").asInt();
        int b = message.get("params").get("arguments").get("b").asInt();
        int additionResult = a + b;
        resultContent.putArray("content")
                .addObject()
                .put("type", "text")
                .put("text", "The sum of " + a + " and " + b + " is " + additionResult + ".");
        sink.send(sse.newEventBuilder()
                .name("message")
                .data(result)
                .build());
    }

    private void executeLongRunningOperation(JsonNode message, String operationId) {
        int duration = message.get("params").get("arguments").get("duration").asInt();
        scheduledExecutorService.schedule(() -> {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("id", operationId);
            result.put("jsonrpc", "2.0");
            ObjectNode resultContent = objectMapper.createObjectNode();
            result.set("result", resultContent);
            resultContent.putArray("content")
                    .addObject()
                    .put("type", "text")
                    .put("text", "Operation completed.");
            sink.send(sse.newEventBuilder()
                    .name("message")
                    .data(result)
                    .build());
        }, duration, TimeUnit.SECONDS);
    }

}
