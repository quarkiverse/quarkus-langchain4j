package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;

class McpTokenTest {

    private static final Logger log = LoggerFactory.getLogger(McpTokenTest.class);
    private static Process process;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url", "http://localhost:8082/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client2.url", "http://localhost:8082/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.client4.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client4.url", "http://localhost:8082/mcp");

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("auth_mcp_server.java");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Inject
    @McpClientName("client1")
    McpClient client1;

    @Inject
    @McpClientName("client2")
    McpClient client2;

    @Inject
    @McpClientName("client4")
    McpClient client4;

    @Test
    public void client1() throws Exception {
        assertThat(getToken(client1)).isEqualTo("Bearer token1");
    }

    @Test
    public void client2() throws Exception {
        assertThat(getToken(client2)).isEqualTo("Bearer token2");
    }

    @Test
    public void client4() throws Exception {
        // there is no McpClientAuthProvider specific for client4, so the global one (without any McpClientName qualifier) should be used
        assertThat(getToken(client4)).isEqualTo("Bearer token-global");
    }

    private String getToken(McpClient client) {
        return client.executeTool(ToolExecutionRequest.builder().name("getToken").build()).resultText();
    }

    @ApplicationScoped
    @McpClientName("client1")
    public static class AuthProviderForClient1 implements McpClientAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer token1";
        }

    }

    @ApplicationScoped
    @McpClientName("client2")
    @McpClientName("client3")
    public static class AuthProviderForClient2and3 implements McpClientAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer token2";
        }

    }

    @ApplicationScoped
    public static class AuthProviderGlobal implements McpClientAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer token-global";
        }

    }

}
