package io.quarkiverse.langchain4j.mcp.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.mcp.test.mock.LegacyMcpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkus.test.QuarkusUnitTest;

public class McpCombinedHeadersLegacyTest extends AbstractMcpCombinedHeadersTest {

    private static final String MCP_PATH = "/mcp/combined-headers-legacy";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMcpCombinedHeadersTest.class,
                            AbstractMcpCombinedHeadersTest.ToolNameHeadersSupplier.class)
                    .addPackage(McpMockServer.class.getPackage())
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.protocol-version=%s
                            quarkus.langchain4j.mcp.client1.url=%s
                            quarkus.langchain4j.mcp.client1.header.%s=%s
                            quarkus.langchain4j.mcp.client1.header.%s=%s
                            quarkus.langchain4j.mcp.client1.cache-tool-list=false
                            """.formatted(LegacyMcpMockServer.PROTOCOL_VERSION, wiremockUrlForConfig(MCP_PATH),
                            STATIC_HEADER, STATIC_HEADER_VALUE,
                            OVERRIDDEN_HEADER, OVERRIDDEN_HEADER_FALLBACK)),
                            "application.properties"));

    @Override
    protected McpMockServer<?> mcpServer() {
        return McpMockServer.legacy(wiremock(), MCP_PATH);
    }
}
