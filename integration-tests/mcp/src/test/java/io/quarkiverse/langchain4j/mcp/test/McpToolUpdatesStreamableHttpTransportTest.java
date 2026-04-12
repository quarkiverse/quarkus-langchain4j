package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;

class McpToolUpdatesStreamableHttpTransportTest {

    private static final Logger log = LoggerFactory.getLogger(McpToolUpdatesStreamableHttpTransportTest.class);
    private static Process process;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url", "http://localhost:8082/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.subsidiary-channel", "true")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("tool_list_updates_mcp_server.java");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            McpServerHelper.destroyProcessTree(process);
        }
    }

    @Test
    void verifyToolListUpdates() {
        List<ToolSpecification> tools = mcpClient.listTools();
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).name()).isEqualTo("registerNewTool");

        // trigger the registration of a new tool on the server
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("registerNewTool")
                .arguments("{}")
                .build());

        // check that the client has received a tool list notification and updated its tool list
        List<ToolSpecification> toolsAfterAddingANewTool = mcpClient.listTools();
        assertThat(toolsAfterAddingANewTool).hasSize(2);

        assertThat(findToolSpecificationByName(toolsAfterAddingANewTool, "registerNewTool"))
                .isNotNull();
        assertThat(findToolSpecificationByName(toolsAfterAddingANewTool, "toLowerCase"))
                .isNotNull();
    }

    private ToolSpecification findToolSpecificationByName(List<ToolSpecification> toolSpecifications, String name) {
        return toolSpecifications.stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
