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

class OidcClientMcpRootsCallbackTest {

    private static Process process;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class, MockOidcTokenEndpoint.class))
            .overrideConfigKey("quarkus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.oidc-client-name", "provider1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.url", "http://localhost:8082/mcp")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.protocol-version", "2025-11-25")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.roots", "Workspace=file:///workspace")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.tool-execution-timeout", "5s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.auto-health-check", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.auth-server-url", "http://localhost:8081")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.discovery-enabled", "false")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.token-path", "/oidc/provider1/token")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.client-id", "c1")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.credentials.secret", "s1")
            .overrideRuntimeConfigKey("quarkus.oidc-client.provider1.grant.type", "client")
            .overrideConfigKey("quarkus.langchain4j.mcp.refreshing.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.refreshing.oidc-client-name", "refreshing")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.refreshing.url", "http://localhost:8082/mcp")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.refreshing.protocol-version", "2025-11-25")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.refreshing.roots", "Workspace=file:///workspace")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.refreshing.tool-execution-timeout", "10s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.refreshing.auto-health-check", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.mcp.refreshing.subsidiary-channel", "true")
            .overrideRuntimeConfigKey("quarkus.oidc-client.refreshing.auth-server-url", "http://localhost:8081")
            .overrideRuntimeConfigKey("quarkus.oidc-client.refreshing.discovery-enabled", "false")
            .overrideRuntimeConfigKey("quarkus.oidc-client.refreshing.token-path", "/oidc/refreshing/token")
            .overrideRuntimeConfigKey("quarkus.oidc-client.refreshing.client-id", "refreshing")
            .overrideRuntimeConfigKey("quarkus.oidc-client.refreshing.credentials.secret", "secret")
            .overrideRuntimeConfigKey("quarkus.oidc-client.refreshing.grant.type", "client");

    @BeforeAll
    static void startServer() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("auth_mcp_server.java", 8082);
    }

    @AfterAll
    static void stopServer() {
        if (process != null && process.isAlive()) {
            destroyProcessTree(process);
        }
    }

    @Inject
    @McpClientName("client1")
    McpClient client;

    @Inject
    @McpClientName("refreshing")
    McpClient refreshingClient;

    @Inject
    MockOidcTokenEndpoint tokenEndpoint;

    @Test
    public void rootsCallbackWithOidcClientAuth() {
        assertThat(client.executeTool(ToolExecutionRequest.builder()
                .name("getToken").arguments("{}").build()).resultText())
                .isEqualTo("Bearer token-from-provider1");

        // The server tool calls Roots.list() and can only finish after receiving the client's reply.
        String result = client.executeTool(ToolExecutionRequest.builder()
                .name("readRoots").arguments("{}").build()).resultText();
        assertThat(result).isEqualTo("file:///workspace");
        assertThat(tokenEndpoint.provider1TokenRequests()).isEqualTo(1);
    }

    @Test
    public void rootsCallbackRefreshesExpiredToken() {
        refreshingClient.listTools();
        int refreshesBeforeToolCall = tokenEndpoint.refreshTokenRequests();

        // The server waits until the token used for this tool call has expired before requesting roots.
        String result = refreshingClient.executeTool(ToolExecutionRequest.builder()
                .name("readRootsAfterTokenExpires").arguments("{}").build()).resultText();

        assertThat(result).isEqualTo("file:///workspace");
        assertThat(tokenEndpoint.refreshTokenRequests()).isGreaterThan(refreshesBeforeToolCall);
    }
}
