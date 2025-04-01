package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.McpClientName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class McpClientAndToolProviderCDITest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(MockHttpMcpServer.class)
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.mcp.client1.transport-type=http
                            quarkus.langchain4j.mcp.client1.url=http://localhost:8081/mock-mcp/sse
                            quarkus.langchain4j.mcp.client1.log-requests=true
                            quarkus.langchain4j.mcp.client1.log-responses=true
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            """),
                            "application.properties"));

    @Inject
    @McpClientName("client1")
    Instance<McpClient> clientCDIInstance;

    @Inject
    Instance<ToolProvider> toolProviderCDIInstance;

    @Test
    public void test() {
        McpClient client = clientCDIInstance.get();
        assertThat(client).isNotNull();
        assertThat(ClientProxy.unwrap(client)).isInstanceOf(DefaultMcpClient.class);

        ToolProvider provider = toolProviderCDIInstance.get();
        assertThat(provider).isNotNull();
        assertThat(ClientProxy.unwrap(provider)).isInstanceOf(McpToolProvider.class);
    }

}
