package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that the MCP client auto-detects and falls back to the legacy protocol
 * when connecting to a server that only supports 2025-11-25 (quarkus-mcp-server 1.13.1),
 * using the Quarkus-specific streamable HTTP transport.
 */
class McpVersionAutoDetectionLegacyStreamableHttpTest {

    private static Process process;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url", "http://localhost:8186/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true");
    // no protocol-version configured — should auto-detect as legacy

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("tools_legacy_mcp_server.java", 8186);
    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            McpServerHelper.destroyProcessTree(process);
        }
    }

    @Test
    void autoDetectsLegacyProtocol() {
        DefaultMcpClient unwrapped = (DefaultMcpClient) ClientProxy.unwrap(mcpClient);
        assertThat(unwrapped.isModernProtocol()).isFalse();
    }
}
