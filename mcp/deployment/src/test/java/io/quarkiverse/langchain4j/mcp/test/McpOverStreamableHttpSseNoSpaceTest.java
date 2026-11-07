package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIterable;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that the streamable HTTP transport correctly parses SSE responses whose
 * fields have no space after the colon ({@code data:{...}}), which is valid per the
 * SSE specification and used by real MCP servers such as Spring AI's.
 */
public class McpOverStreamableHttpSseNoSpaceTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMockHttpMcpServer.class, MockHttpMcpServer.class,
                            MockSseNoSpaceHttpMcpServer.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.url=http://localhost:8081/mock-sse-no-space-mcp/mcp
                            quarkus.langchain4j.mcp.client1.log-requests=true
                            quarkus.langchain4j.mcp.client1.log-responses=true
                            quarkus.langchain4j.mcp.client1.tool-execution-timeout=5s
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            """),
                            "application.properties"));

    @Inject
    ToolProvider toolProvider;

    @Test
    public void providingTools() {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        assertThat(toolProviderResult.tools().size()).isEqualTo(3);
        Set<String> toolNames = toolProviderResult.tools().keySet().stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        assertThatIterable(toolNames)
                .containsExactlyInAnyOrder("add", "longRunningOperation", "logging");
    }

    @Test
    public void executingATool() {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

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

        assertThat(toolExecutionResultString).isEqualTo("The sum of 5 and 12 is 17.");
    }
}
