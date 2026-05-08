package io.quarkiverse.langchain4j.oidc.client.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;

public class NamedOidcClientMcpAuthProviderTest {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AbstractMockHttpMcpServer.class, MockProvider1McpServer.class, MockProvider2McpServer.class,
                            MockOidcTokenEndpoint.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.oidc-client-name", "provider1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.url", "http://localhost:8081/mcp1/sse")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.transport-type", "http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.oidc-client-name", "provider2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client2.url", "http://localhost:8081/mcp2/sse")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.auth-server-url", "http://localhost:8081")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.discovery-enabled", "false")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.token-path", "/oidc/provider1/token")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.client-id", "c1")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.credentials.secret", "s1")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.grant.type", "client")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider2.auth-server-url", "http://localhost:8081")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider2.discovery-enabled", "false")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider2.token-path", "/oidc/provider2/token")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider2.client-id", "c2")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider2.credentials.secret", "s2")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider2.grant.type", "client");

    @Inject
    @McpClientName("client1")
    McpClient mcpClient1;

    @Inject
    @McpClientName("client2")
    McpClient mcpClient2;

    @Inject
    ToolProvider toolProvider;

    @Test
    public void client1UsesProvider1Token() {
        ToolProviderResult result = toolProvider.provideTools(null);
        assertThat(result.tools().keySet().stream().map(ToolSpecification::name))
                .contains("tool-from-server1");
    }

    @Test
    public void client2UsesProvider2Token() {
        ToolProviderResult result = toolProvider.provideTools(null);
        assertThat(result.tools().keySet().stream().map(ToolSpecification::name))
                .contains("tool-from-server2");
    }
}
