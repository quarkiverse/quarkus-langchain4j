package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

public abstract class McpDynamicHeadersTestBase {

    @Inject
    @McpClientName("client1")
    McpClient client;

    /**
     * The application contains a CDI bean of type {@link dev.langchain4j.mcp.client.McpHeadersSupplier}
     * ({@link TestMcpHeadersSupplier}) that extracts the tool name from the {@link dev.langchain4j.mcp.client.McpCallContext}
     * and provides it as the value of the {@code X-Tool-Name} HTTP header. The test calls the {@code echoHeader} tool
     * on the MCP server, which returns the value of the specified header, and verifies that the header value
     * matches the tool name.
     */
    @Test
    public void toolNameHeader() {
        String result = client.executeTool(ToolExecutionRequest.builder()
                .name("echoHeader")
                .arguments("{\"headerName\": \"X-Tool-Name\"}")
                .build()).resultText();
        assertThat(result).isEqualTo("echoHeader");
    }

}
