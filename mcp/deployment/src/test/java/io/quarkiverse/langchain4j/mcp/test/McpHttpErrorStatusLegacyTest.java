package io.quarkiverse.langchain4j.mcp.test;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.mcp.test.mock.LegacyMcpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkus.test.QuarkusUnitTest;

public class McpHttpErrorStatusLegacyTest extends AbstractMcpHttpErrorStatusTest {

    private static final String MCP_PATH = "/mcp/http-error-status-legacy";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMcpHttpErrorStatusTest.class)
                    .addPackage(McpMockServer.class.getPackage())
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.protocol-version=%s
                            quarkus.langchain4j.mcp.client1.url=%s
                            quarkus.langchain4j.mcp.client1.log-requests=true
                            quarkus.langchain4j.mcp.client1.log-responses=true
                            quarkus.langchain4j.mcp.client1.cache-tool-list=false
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            """.formatted(LegacyMcpMockServer.PROTOCOL_VERSION, wiremockUrlForConfig(MCP_PATH))),
                            "application.properties"));

    @Override
    protected McpMockServer<?> mcpServer() {
        return McpMockServer.legacy(wiremock(), MCP_PATH);
    }
}
