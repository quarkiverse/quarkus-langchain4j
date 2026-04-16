package io.quarkiverse.langchain4j.mcp.test.apicurio;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A mock Streamable HTTP MCP server endpoint for testing.
 * Implements just enough of the MCP protocol to respond to tool calls.
 */
@Path("/mock-streamable-mcp")
public class MockStreamableHttpMcpServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public String handle(String body) throws Exception {
        JsonNode request = MAPPER.readTree(body);
        String method = request.has("method") ? request.get("method").asText() : null;

        if ("initialize".equals(method)) {
            return handleInitialize(request);
        } else if ("notifications/initialized".equals(method)) {
            return "";
        } else if ("tools/list".equals(method)) {
            return handleToolsList(request);
        } else if ("tools/call".equals(method)) {
            return handleToolsCall(request);
        } else if ("ping".equals(method)) {
            return handlePing(request);
        }

        return createErrorResponse(request, -32601, "Method not found: " + method);
    }

    private String handleInitialize(JsonNode request) throws Exception {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2025-03-26");

        ObjectNode capabilities = MAPPER.createObjectNode();
        ObjectNode tools = MAPPER.createObjectNode();
        tools.put("listChanged", false);
        capabilities.set("tools", tools);
        result.set("capabilities", capabilities);

        ObjectNode serverInfo = MAPPER.createObjectNode();
        serverInfo.put("name", "mock-mcp-server");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);

        return createSuccessResponse(request, result);
    }

    private String handleToolsList(JsonNode request) throws Exception {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode toolsArray = MAPPER.createArrayNode();

        ObjectNode tool = MAPPER.createObjectNode();
        tool.put("name", "registry_discovered_add");
        tool.put("description", "Add two numbers together");
        ObjectNode inputSchema = MAPPER.createObjectNode();
        inputSchema.put("type", "object");
        ObjectNode properties = MAPPER.createObjectNode();
        ObjectNode a = MAPPER.createObjectNode();
        a.put("type", "number");
        a.put("description", "First number");
        properties.set("a", a);
        ObjectNode b = MAPPER.createObjectNode();
        b.put("type", "number");
        b.put("description", "Second number");
        properties.set("b", b);
        inputSchema.set("properties", properties);
        ArrayNode required = MAPPER.createArrayNode();
        required.add("a");
        required.add("b");
        inputSchema.set("required", required);
        tool.set("inputSchema", inputSchema);
        toolsArray.add(tool);

        result.set("tools", toolsArray);
        return createSuccessResponse(request, result);
    }

    private String handleToolsCall(JsonNode request) throws Exception {
        JsonNode params = request.get("params");
        String toolName = params.get("name").asText();

        if ("registry_discovered_add".equals(toolName)) {
            JsonNode args = params.get("arguments");
            double sum = args.get("a").asDouble() + args.get("b").asDouble();

            ObjectNode result = MAPPER.createObjectNode();
            ArrayNode content = MAPPER.createArrayNode();
            ObjectNode textContent = MAPPER.createObjectNode();
            textContent.put("type", "text");
            textContent.put("text", String.valueOf(sum));
            content.add(textContent);
            result.set("content", content);
            result.put("isError", false);
            return createSuccessResponse(request, result);
        }

        return createErrorResponse(request, -32602, "Unknown tool: " + toolName);
    }

    private String handlePing(JsonNode request) throws Exception {
        return createSuccessResponse(request, MAPPER.createObjectNode());
    }

    private String createSuccessResponse(JsonNode request, JsonNode result) throws Exception {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (request.has("id")) {
            response.set("id", request.get("id"));
        }
        response.set("result", result);
        return MAPPER.writeValueAsString(response);
    }

    private String createErrorResponse(JsonNode request, int code, String message) throws Exception {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (request.has("id")) {
            response.set("id", request.get("id"));
        }
        ObjectNode error = MAPPER.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        return MAPPER.writeValueAsString(response);
    }
}
