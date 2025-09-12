package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verify that when some MCP clients are configured, but
 * quarkus.langchain4j.mcp.generate-tool-provider=false, then no tool
 * provider will be generated out of the box.
 */
public class NoAutomaticToolProviderTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMockHttpMcpServer.class, MockHttpMcpServer.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=http
                            quarkus.langchain4j.mcp.client1.url=http://localhost:8081/mock-mcp/sse
                            quarkus.langchain4j.mcp.generate-tool-provider=false
                            """),
                            "application.properties"));

    @Inject
    @McpClientName("client1")
    Instance<McpClient> clientCDIInstance;

    @Inject
    Instance<ToolProvider> toolProviderCDIInstance;

    @Test
    public void test() {
        assertThat(clientCDIInstance.isResolvable()).isTrue();
        assertThat(toolProviderCDIInstance.isResolvable()).isFalse();
    }

}
