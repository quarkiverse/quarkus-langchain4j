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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that an application can have two MCP clients connected to the same server, one forced to the
 * legacy protocol (2025-11-25) and one auto-detecting the protocol version (which picks 2026-07-28), and
 * that both work.
 */
class McpMixedProtocolVersionsStreamableHttpTest {

    private static Process process;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.legacy.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.legacy.url", "http://localhost:8188/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.legacy.protocol-version", "2025-11-25")
            .overrideConfigKey("quarkus.langchain4j.mcp.legacy.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.legacy.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.detected.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.detected.url", "http://localhost:8188/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.detected.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.detected.log-responses", "true");
    // no protocol-version configured for "detected" — auto-detection should pick modern

    @Inject
    @McpClientName("legacy")
    McpClient legacyClient;

    @Inject
    @McpClientName("detected")
    McpClient detectedClient;

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("tools_mcp_server.java", 8188);
    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            McpServerHelper.destroyProcessTree(process);
        }
    }

    @Test
    void eachClientUsesItsOwnProtocolVersion() {
        assertThat(((DefaultMcpClient) ClientProxy.unwrap(legacyClient)).isModernProtocol()).isFalse();
        assertThat(((DefaultMcpClient) ClientProxy.unwrap(detectedClient)).isModernProtocol()).isTrue();
    }

    @Test
    void bothClientsExecuteTools() {
        assertThat(echo(legacyClient, "legacy")).isEqualTo("legacy");
        assertThat(echo(detectedClient, "detected")).isEqualTo("detected");
    }

    private static String echo(McpClient client, String input) {
        return client.executeTool(ToolExecutionRequest.builder()
                .name("echoString")
                .arguments("{\"input\": \"" + input + "\"}")
                .build()).resultText();
    }
}
