package io.quarkiverse.langchain4j.oidc.client.mcp.test;

import static io.quarkiverse.langchain4j.oidc.client.mcp.test.McpServerHelper.destroyProcessTree;
import static io.quarkiverse.langchain4j.oidc.client.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.oidc.client.mcp.test.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;

class NamedOidcClientMcpAuthProviderTest {

    private static Process process1;
    private static Process process2;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class, MockOidcTokenEndpoint.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.oidc-client-name", "provider1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.url", "http://localhost:8082/mcp/sse")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.transport-type", "http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.oidc-client-name", "provider2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client2.url", "http://localhost:8083/mcp/sse")
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

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process1 = startServerHttp("auth_mcp_server.java", 8082);
        process2 = startServerHttp("auth_mcp_server.java", 8083);
    }

    @AfterAll
    static void teardown() {
        if (process1 != null && process1.isAlive()) {
            destroyProcessTree(process1);
        }
        if (process2 != null && process2.isAlive()) {
            destroyProcessTree(process2);
        }
    }

    @Inject
    @McpClientName("client1")
    McpClient mcpClient1;

    @Inject
    @McpClientName("client2")
    McpClient mcpClient2;

    @Test
    public void client1UsesProvider1Token() {
        assertThat(getToken(mcpClient1)).isEqualTo("Bearer token-from-provider1");
    }

    @Test
    public void client2UsesProvider2Token() {
        assertThat(getToken(mcpClient2)).isEqualTo("Bearer token-from-provider2");
    }

    private String getToken(McpClient client) {
        return client.executeTool(ToolExecutionRequest.builder().name("getToken").build()).resultText();
    }
}
