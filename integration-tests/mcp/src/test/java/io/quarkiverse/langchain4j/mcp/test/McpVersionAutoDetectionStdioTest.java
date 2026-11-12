package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that the MCP client auto-detects the modern protocol (2026-07-28) when no
 * protocol-version is configured and the server supports it, using the stdio transport.
 */
class McpVersionAutoDetectionStdioTest {

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "stdio")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.command",
                    "jbang,--quiet,--fresh,run,src/test/resources/tools_mcp_server.java")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true");
    // no protocol-version configured — auto-detection should pick modern

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @BeforeAll
    static void setup() throws Exception {
        copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready("tools_mcp_server.java");
        skipTestsIfJbangNotAvailable();
    }

    @Test
    void autoDetectsModernProtocol() {
        DefaultMcpClient unwrapped = (DefaultMcpClient) ClientProxy.unwrap(mcpClient);
        assertThat(unwrapped.isModernProtocol()).isTrue();
    }
}
