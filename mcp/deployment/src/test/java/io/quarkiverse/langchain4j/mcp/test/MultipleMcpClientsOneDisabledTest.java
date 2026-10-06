package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.mcp.test.mock.ModernMcpMockServer;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class MultipleMcpClientsOneDisabledTest extends WiremockAware {

    private static final String CLIENT1_PATH = "/mcp/multiple-clients-one-disabled/client1";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addPackage(McpMockServer.class.getPackage())
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.protocol-version=%1$s
                            quarkus.langchain4j.mcp.client1.url=%2$s
                            quarkus.langchain4j.mcp.client2.enabled=false
                            quarkus.langchain4j.mcp.client2.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client2.protocol-version=%1$s
                            quarkus.langchain4j.mcp.client2.url=http://localhost/never-contacted/mcp
                            """.formatted(ModernMcpMockServer.PROTOCOL_VERSION, wiremockUrlForConfig(CLIENT1_PATH))),
                            "application.properties"));

    @Inject
    @McpClientName("client1")
    Instance<McpClient> client1Instance;

    @Inject
    @McpClientName("client2")
    Instance<McpClient> client2Instance;

    @Inject
    ToolProvider toolProvider;

    @BeforeEach
    void setUpMcpServer() {
        McpMockServer.modern(wiremock(), CLIENT1_PATH).reset().stubInitialization().stubTools(McpTool.ADD);
    }

    @Test
    public void oneClientDisabled() {
        assertThat(client1Instance.isResolvable()).isTrue();
        assertThat(client2Instance.isResolvable()).isFalse();

        ToolProviderResult result = toolProvider.provideTools(null);
        assertThat(result.tools().keySet())
                .extracting(ToolSpecification::name)
                .containsExactly("add");
    }
}
