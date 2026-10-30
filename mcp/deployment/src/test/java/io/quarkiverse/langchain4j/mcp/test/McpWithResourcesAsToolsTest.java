package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.mcp.test.mock.ModernMcpMockServer;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class McpWithResourcesAsToolsTest extends WiremockAware {

    private static final String MCP_PATH = "/mcp/resources-as-tools";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(McpMockServer.class.getPackage())
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.expose-resources-as-tools=true
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.protocol-version=%s
                            quarkus.langchain4j.mcp.client1.url=%s
                            quarkus.langchain4j.mcp.client1.log-requests=true
                            quarkus.langchain4j.mcp.client1.log-responses=true
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            quarkus.langchain4j.mcp.client1.tool-execution-timeout=1s
                            """.formatted(ModernMcpMockServer.PROTOCOL_VERSION, wiremockUrlForConfig(MCP_PATH))),
                            "application.properties"));

    @Inject
    ToolProvider toolProvider;

    @BeforeEach
    void setUpMcpServer() {
        McpMockServer.modern(wiremock(), MCP_PATH)
                .reset()
                .stubInitialization()
                .stubTools(McpTool.ADD);
    }

    @Test
    public void providingTools() {
        // obtain a list of tools from the MCP server
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);

        // verify the list of tools
        assertThat(toolProviderResult.tools().keySet())
                .extracting(ToolSpecification::name)
                .containsExactlyInAnyOrder("add", "get_resource", "list_resources");
    }
}
