package io.quarkiverse.langchain4j.mcp.test.tls;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.*;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;

public abstract class McpTlsTestBase {

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @Inject
    @McpClientName("client2")
    McpClient clientWithBadTrustStore;

    @Test
    void testAuthenticationSuccessful() throws Exception {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"abc\"}")
                .build();
        Assertions.assertThat(mcpClient.executeTool(toolExecutionRequest)).isEqualTo("abc");
    }

    @Test
    void testClientWithBadTrustStore() throws Exception {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"abc\"}")
                .build();
        Assertions.assertThatThrownBy(() -> clientWithBadTrustStore.executeTool(toolExecutionRequest))
                .isNotNull();
    }

}
