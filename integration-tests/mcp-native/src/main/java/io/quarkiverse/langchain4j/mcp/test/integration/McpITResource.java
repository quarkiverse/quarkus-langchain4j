package io.quarkiverse.langchain4j.mcp.test.integration;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

@Path("/")
public class McpITResource {

    @Inject
    @McpClientName("client-streamable-http")
    McpClient mcpClientStreamableHttp;

    @Inject
    @McpClientName("client-stdio")
    McpClient mcpClientStdio;

    @GET
    @Path("/streamable-http")
    public String callToolOverStreamableHttp() {
        callEchoTool(mcpClientStreamableHttp);
        return "OK";
    }

    @GET
    @Path("/stdio")
    public String callToolOverStdio() {
        callEchoTool(mcpClientStdio);
        return "OK";
    }

    private void callEchoTool(McpClient client) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"abc\"}")
                .build();
        String result = client.executeTool(request).resultText();
        if (!result.equals("abc")) {
            throw new IllegalStateException(result);
        }
    }
}
