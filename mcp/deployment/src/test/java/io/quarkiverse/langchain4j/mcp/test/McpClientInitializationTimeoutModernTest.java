package io.quarkiverse.langchain4j.mcp.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.ModernMcpMockServer;
import io.quarkus.test.QuarkusUnitTest;

public class McpClientInitializationTimeoutModernTest extends AbstractMcpClientInitializationTimeoutTest {

    private static final String MCP_PATH = "/mcp/initialization-timeout-modern";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMcpClientInitializationTimeoutTest.class)
                    .addPackage(McpMockServer.class.getPackage()))
            .overrideConfigKey("quarkus.langchain4j.mcp.short-timeout.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.long-timeout.transport-type", "streamable-http")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.short-timeout.protocol-version",
                    ModernMcpMockServer.PROTOCOL_VERSION)
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.short-timeout.url", wiremockUrlForConfig(MCP_PATH))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.short-timeout.initialization-timeout", "500ms")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.long-timeout.protocol-version",
                    ModernMcpMockServer.PROTOCOL_VERSION)
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.long-timeout.url", wiremockUrlForConfig(MCP_PATH))
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.long-timeout.initialization-timeout", "10s");

    @Override
    protected McpMockServer<?> mcpServer() {
        return McpMockServer.modern(wiremock(), MCP_PATH);
    }
}
