package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that x-mcp-header annotations on tool parameters cause the client
 * to send Mcp-Param-{Name} headers on tools/call requests via the Quarkus
 * streamable HTTP transport.
 */
public class McpParamHeadersStreamableHttpTest {

    private static Process process;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url",
                    "http://localhost:8082/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("param_header_mcp_server.java");

    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            McpServerHelper.destroyProcessTree(process);
        }
    }

    @BeforeEach
    void setupEach() {
        // the list of tools has to be already known to be able to tell which HTTP headers to add
        mcpClient.listTools();
    }

    @Test
    public void paramHeaderSentForStringParameter() {
        // the list of tools has to be already known to be able to tell which HTTP headers to add
        mcpClient.listTools();
        String result = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("regionEcho")
                .arguments("{\"region\": \"us-west1\", \"value\": \"hello\"}")
                .build()).resultText();
        assertThat(result).isEqualTo("header=us-west1,body=us-west1");
    }

    @Test
    public void paramHeaderWithCustomName() {
        String result = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("customHeaderName")
                .arguments("{\"region\": \"eu-central\", \"value\": \"world\"}")
                .build()).resultText();
        assertThat(result).isEqualTo("header=eu-central,body=eu-central");
    }

    @Test
    public void paramHeaderWithIntegerAndBoolean() {
        String result = mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("typedHeaders")
                .arguments("{\"count\": 42, \"verbose\": true, \"value\": \"test\"}")
                .build()).resultText();
        assertThat(result).isEqualTo("countHeader=42,verboseHeader=true");
    }
}
