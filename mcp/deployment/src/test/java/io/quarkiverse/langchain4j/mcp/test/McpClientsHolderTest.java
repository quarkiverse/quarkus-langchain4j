package io.quarkiverse.langchain4j.mcp.test;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.McpClientName;
import io.quarkiverse.langchain4j.mcp.McpClients;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Test the McpClients holder, which is a CDI bean that provides
 * references to all configured MCP clients.
 */
public class McpClientsHolderTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(MockHttpMcpServer.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.mcp.client1.transport-type=http
                            quarkus.langchain4j.mcp.client1.url=http://localhost:8081/mock-mcp/sse

                            quarkus.langchain4j.mcp.client2.transport-type=http
                            quarkus.langchain4j.mcp.client2.url=http://localhost:8081/mock-mcp/sse
                            """),
                            "application.properties"));

    @Inject
    @McpClientName("client1")
    McpClient client1;

    @Inject
    @McpClientName("client2")
    McpClient client2;

    @Inject
    McpClients mcpClients;

    @Test
    public void test() {
        Assertions.assertEquals(2, mcpClients.listAll().size());
        Assertions.assertEquals(client1, mcpClients.get("client1"));
        Assertions.assertEquals(client2, mcpClients.get("client2"));
    }
}
