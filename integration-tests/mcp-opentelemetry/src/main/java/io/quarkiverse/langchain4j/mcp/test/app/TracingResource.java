package io.quarkiverse.langchain4j.mcp.test.app;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.opentelemetry.api.common.AttributeKey;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.arc.profile.IfBuildProfile;

/**
 * Lets the native tests call a tool over each transport and see the resulting spans.
 */
@IfBuildProfile("prod")
@Path("/tracing")
public class TracingResource {

    @Inject
    @McpClientName("stdio")
    McpClient stdioClient;

    @Inject
    @McpClientName("http")
    McpClient httpClient;

    @Inject
    InMemorySpanExporter exporter;

    /**
     * Calls a tool that returns the traceparent that the server received in {@code _meta}.
     */
    @GET
    @Path("/call/{client}")
    public String call(@PathParam("client") String client) {
        McpClient mcpClient = client.equals("stdio") ? stdioClient : httpClient;
        return mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"traceparent\"}")
                .build())
                .resultText();
    }

    @GET
    @Path("/spans")
    public List<Map<String, Object>> spans() {
        return exporter.getSpans().stream().map(span -> {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("name", span.getName());
            json.put("kind", span.getKind().name());
            json.put("traceId", span.getTraceId());
            json.put("spanId", span.getSpanId());
            json.put("parentSpanId", span.getParentSpanId());
            json.put("mcpMethodName", span.getAttributes().get(AttributeKey.stringKey("mcp.method.name")));
            return json;
        }).toList();
    }
}
