package io.quarkiverse.langchain4j.mcp.test.apicurio;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A minimal fake of the Apicurio Registry REST API and of a Streamable HTTP MCP server, served by the test application
 * itself, so a server can be discovered and called without a registry container or an external MCP server.
 * The MCP server records the {@code traceparent} it receives in the {@code _meta} of each {@code tools/call} request.
 */
public class FakeRegistryAndMcpServer {

    static final int PORT = 8095;
    static final String ARTIFACT_ID = "echo-server";
    static final String SERVER_KEY = "default/" + ARTIFACT_ID;
    static final String TOOL_NAME = "echo";

    static final List<String> RECEIVED_TRACEPARENTS = new CopyOnWriteArrayList<>();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Path("/apis/registry/v3/groups/{groupId}/artifacts/{artifactId}")
    public static class FakeRegistry {

        @GET
        @jakarta.ws.rs.Produces(MediaType.APPLICATION_JSON)
        public Map<String, Object> artifact(@PathParam("groupId") String groupId,
                @PathParam("artifactId") String artifactId) {
            return Map.of(
                    "groupId", groupId,
                    "artifactId", artifactId,
                    "artifactType", "MCP_TOOL",
                    "labels", Map.of("mcp-server-url", "http://localhost:" + PORT + "/mcp"));
        }
    }

    @Path("/mcp")
    public static class FakeMcpServer {

        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        @jakarta.ws.rs.Produces(MediaType.APPLICATION_JSON)
        public Response handle(JsonNode request) {
            String method = request.path("method").asText();
            if (!request.has("id")) {
                // notifications, e.g. notifications/initialized
                return Response.accepted().build();
            }
            ObjectNode result = MAPPER.createObjectNode();
            switch (method) {
                case "initialize" -> {
                    result.put("protocolVersion", "2025-11-25");
                    result.putObject("capabilities").putObject("tools");
                    result.putObject("serverInfo").put("name", "fake").put("version", "1.0.0");
                    return Response.ok(rpcResult(request, result)).header("Mcp-Session-Id", "fake-session").build();
                }
                case "tools/list" -> {
                    ObjectNode tool = result.putArray("tools").addObject();
                    tool.put("name", TOOL_NAME);
                    tool.put("description", "Echoes a message");
                    tool.putObject("inputSchema").put("type", "object");
                }
                case "tools/call" -> {
                    JsonNode traceparent = request.path("params").path("_meta").get("traceparent");
                    RECEIVED_TRACEPARENTS.add(traceparent == null ? "none" : traceparent.asText());
                    result.putArray("content").addObject().put("type", "text").put("text", "echoed");
                    result.put("isError", false);
                }
                case "ping" -> {
                }
                default -> {
                    ObjectNode error = MAPPER.createObjectNode();
                    error.put("jsonrpc", "2.0");
                    error.set("id", request.get("id"));
                    error.putObject("error").put("code", -32601).put("message", "Method not found: " + method);
                    return Response.ok(error).build();
                }
            }
            return Response.ok(rpcResult(request, result)).build();
        }

        private static ObjectNode rpcResult(JsonNode request, ObjectNode result) {
            ObjectNode response = MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", request.get("id"));
            response.set("result", result);
            return response;
        }
    }

    @ApplicationScoped
    public static class InMemorySpanExporterProducer {

        @Produces
        @ApplicationScoped
        InMemorySpanExporter exporter() {
            return InMemorySpanExporter.create();
        }
    }
}
