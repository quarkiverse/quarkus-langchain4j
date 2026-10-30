package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.mcp.runtime.QuarkusMcpToolProvider;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;

/**
 * Test MCP clients over the streamable HTTP transport, running against a mock MCP server.
 * Subclasses choose the protocol version spoken by the client and the server.
 */
public abstract class AbstractMcpOverHttpTransportTest extends WiremockAware {

    @Inject
    ToolProvider toolProvider;

    /**
     * Creates the mock server for the protocol version under test. The client is configured
     * with a 1-second tool execution timeout.
     */
    protected abstract McpMockServer<?> mcpServer();

    @BeforeEach
    void setUpMcpServer() {
        mcpServer()
                .reset()
                .stubInitialization()
                .stubTools(McpTool.ADD, McpTool.LONG_RUNNING_OPERATION)
                .stubToolCall("add", "{\"a\": 5, \"b\": 12}", "The sum of 5 and 12 is 17.")
                .stubSlowToolCall("longRunningOperation", Duration.ofSeconds(5), "Operation completed.");
    }

    @Test
    public void toolProviderShouldBeMcpBased() {
        assertThat(ClientProxy.unwrap(toolProvider)).isInstanceOf(QuarkusMcpToolProvider.class);
    }

    @Test
    public void providingTools() {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        assertThat(toolProviderResult.tools().keySet())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("add", "longRunningOperation");

        ToolSpecification addTool = findToolByName(toolProviderResult, "add");
        assertThat(addTool.description()).isEqualTo("Adds two numbers");
        JsonNumberSchema a = (JsonNumberSchema) addTool.parameters().properties().get("a");
        assertThat(a.description()).isEqualTo("First number");
        JsonNumberSchema b = (JsonNumberSchema) addTool.parameters().properties().get("b");
        assertThat(b.description()).isEqualTo("Second number");

        ToolSpecification longRunningOperationTool = findToolByName(toolProviderResult, "longRunningOperation");
        assertThat(longRunningOperationTool.description())
                .isEqualTo("Demonstrates a long running operation with progress updates");
        JsonNumberSchema duration = (JsonNumberSchema) longRunningOperationTool.parameters().properties().get("duration");
        assertThat(duration.description()).isEqualTo("Duration of the operation in seconds");
        JsonNumberSchema steps = (JsonNumberSchema) longRunningOperationTool.parameters().properties().get("steps");
        assertThat(steps.description()).isEqualTo("Number of steps in the operation");
    }

    @Test
    public void executingATool() {
        String result = executeTool("add", "{\"a\": 5, \"b\": 12}");
        assertThat(result).isEqualTo("The sum of 5 and 12 is 17.");
    }

    @Test
    public void timeout() {
        String result = executeTool("longRunningOperation", "{\"duration\": 5, \"steps\": 1}");
        assertThat(result).isEqualTo("There was a timeout executing the tool");
    }

    protected String executeTool(String toolName, String arguments) {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);
        ToolExecutor executor = toolProviderResult.tools().get(findToolByName(toolProviderResult, toolName));
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(toolName)
                .arguments(arguments)
                .build();
        return executor.execute(request, null);
    }

    private static ToolSpecification findToolByName(ToolProviderResult toolProviderResult, String name) {
        return toolProviderResult.tools().keySet().stream()
                .filter(toolSpecification -> toolSpecification.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
