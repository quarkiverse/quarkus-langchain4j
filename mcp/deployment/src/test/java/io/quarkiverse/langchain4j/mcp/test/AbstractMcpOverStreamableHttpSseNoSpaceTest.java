package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;

/**
 * Verifies that the streamable HTTP transport correctly parses SSE responses whose
 * fields have no space after the colon ({@code data:{...}}), which is valid per the
 * SSE specification and used by real MCP servers such as Spring AI's.
 * Subclasses choose the protocol version spoken by the client and the server.
 */
public abstract class AbstractMcpOverStreamableHttpSseNoSpaceTest extends WiremockAware {

    @Inject
    ToolProvider toolProvider;

    protected abstract McpMockServer<?> mcpServer();

    @BeforeEach
    void setUpMcpServer() {
        mcpServer()
                .reset()
                .responseFormat(McpMockServer.ResponseFormat.SSE_NO_SPACE)
                .stubInitialization()
                .stubTools(McpTool.ADD)
                .stubToolCall("add", "{\"a\": 5, \"b\": 12}", "The sum of 5 and 12 is 17.");
    }

    @Test
    public void providingTools() {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        assertThat(toolProviderResult.tools().keySet())
                .extracting(ToolSpecification::name)
                .containsExactly("add");
    }

    @Test
    public void executingATool() {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);
        ToolExecutor executor = toolProviderResult.tools().values().iterator().next();
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("add")
                .arguments("{\"a\": 5, \"b\": 12}")
                .build();

        assertThat(executor.execute(toolExecutionRequest, null)).isEqualTo("The sum of 5 and 12 is 17.");
    }
}
