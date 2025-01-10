package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIterable;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Test MCP clients over an HTTP transport.
 * This is a very rudimentary test that runs against a mock MCP server. The plan is
 * to replace it with a more proper MCP server once we have an appropriate Java SDK ready for it.
 */
public class McpOverHttpTransportTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(MockHttpMcpServer.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.mcp.client1.transport-type=http
                            quarkus.langchain4j.mcp.client1.url=http://localhost:${quarkus.http.test-port}/mock-mcp/sse
                            quarkus.langchain4j.mcp.client1.log-requests=true
                            quarkus.langchain4j.mcp.client1.log-responses=true
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            quarkus.langchain4j.mcp.client1.tool-execution-timeout=1s
                            """),
                            "application.properties"));

    @Inject
    ToolProvider toolProvider;

    @Test
    public void toolProviderShouldBeMcpBased() {
        assertThat(ClientProxy.unwrap(toolProvider)).isInstanceOf(McpToolProvider.class);
    }

    @Test
    public void providingTools() {
        // obtain a list of tools from the MCP server
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        // verify the list of tools
        assertThat(toolProviderResult.tools().size()).isEqualTo(2);
        Set<String> toolNames = toolProviderResult.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        assertThatIterable(toolNames)
                .containsExactlyInAnyOrder("add", "longRunningOperation");

        // verify the 'add' tool
        ToolSpecification addTool = findToolByName(toolProviderResult, "add");
        assertThat(addTool.description()).isEqualTo("Adds two numbers");
        JsonNumberSchema a = (JsonNumberSchema) addTool.parameters().properties().get("a");
        assertThat(a.description().equals("First number"));
        JsonNumberSchema b = (JsonNumberSchema) addTool.parameters().properties().get("b");
        assertThat(b.description().equals("Second number"));

        // verify the 'longRunningOperation' tool
        ToolSpecification longRunningOperationTool = findToolByName(toolProviderResult, "longRunningOperation");
        assertThat(longRunningOperationTool.description())
                .isEqualTo("Demonstrates a long running operation with progress updates");
        JsonNumberSchema duration = (JsonNumberSchema) longRunningOperationTool.parameters().properties().get("duration");
        assertThat(duration.description().equals("Duration of the operation in seconds"));
        JsonNumberSchema steps = (JsonNumberSchema) longRunningOperationTool.parameters().properties().get("steps");
        assertThat(steps.description().equals("Number of steps in the operation"));
    }

    private ToolSpecification findToolByName(ToolProviderResult toolProviderResult, String name) {
        return toolProviderResult.tools().keySet().stream()
                .filter(toolSpecification -> toolSpecification.name().equals(name))
                .findFirst()
                .get();
    }

    @Test
    public void executingATool() {
        // obtain tools from the server
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        // find the 'add' tool and execute it on the MCP server
        ToolExecutor executor = toolProviderResult.tools().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals("add"))
                .findFirst()
                .get()
                .getValue();
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("add")
                .arguments("{\"a\": 5, \"b\": 12}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);

        // validate the tool execution result
        assertThat(toolExecutionResultString).isEqualTo("The sum of 5 and 12 is 17.");
    }

    @Test
    public void timeout() {
        // obtain tools from the server
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        // find the 'longRunningOperation' tool and execute it on the MCP server
        ToolExecutor executor = toolProviderResult.tools().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals("longRunningOperation"))
                .findFirst()
                .get()
                .getValue();
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("longRunningOperation")
                .arguments("{\"duration\": 5, \"steps\": 1}")
                .build();
        String toolExecutionResultString = executor.execute(toolExecutionRequest, null);

        // validate the tool execution result
        assertThat(toolExecutionResultString).isEqualTo("There was a timeout executing the tool");
    }

    @Inject
    MockHttpMcpServer server;

    @Test
    public void respondingToServerPing() throws ExecutionException, InterruptedException, TimeoutException {
        // force the server to send a ping request, the McpClient under test should respond to it
        long operationId = server.sendPing();
        // wait for the server to confirm reception of the ping response
        server.pendingPings.get(operationId).get(5, TimeUnit.SECONDS);
    }
}
