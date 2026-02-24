package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

public abstract class McpStaticHeadersTestBase {

    @Inject
    @McpClientName("client1")
    McpClient client;

    @Test
    public void customHeader() {
        String result = client.executeTool(ToolExecutionRequest.builder()
                .name("echoHeader")
                .arguments("{\"headerName\": \"X-Custom-Header\"}")
                .build()).resultText();
        assertThat(result).isEqualTo("custom-value");
    }

    @Test
    public void anotherHeader() {
        String result = client.executeTool(ToolExecutionRequest.builder()
                .name("echoHeader")
                .arguments("{\"headerName\": \"X-Another-Header\"}")
                .build()).resultText();
        assertThat(result).isEqualTo("another-value");
    }

}
