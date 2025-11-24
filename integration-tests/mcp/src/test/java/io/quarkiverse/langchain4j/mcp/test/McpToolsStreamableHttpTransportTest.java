package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.quarkus.test.QuarkusUnitTest;

class McpToolsStreamableHttpTransportTest extends McpToolsTestBase {

    private static final Logger log = LoggerFactory.getLogger(McpToolsStreamableHttpTransportTest.class);
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
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.tool-execution-timeout", "5s")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("tools_mcp_server.java");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }

    @Test
    void reinitializesSessionWhenMissingFromServer() throws Exception {
        // Initial call to establish session and verify MCP server is up
        List<ToolSpecification> toolSpecifications = mcpClient.listTools();
        assertNotNull(toolSpecifications, "Tool specifications should not be null");
        assertDoesNotThrow(() -> mcpClient.checkHealth(), "Health check should pass initially");

        // Simulate server crash
        process.destroyForcibly();

        // Expect failure as server is down
        assertThrows(
                Exception.class, () -> mcpClient.checkHealth(), "Expected exception when server is down");

        // Restart the server
        process = startServerHttp("tools_mcp_server.java");

        // Subsequent call should reinitialize session and succeed
        assertDoesNotThrow(
                () -> mcpClient.checkHealth(), "Session should be reinitialized and health check should pass");
    }

    //    /**
    //     * Verify that the MCP client fails gracefully when the server returns a 404.
    //     */
    //    @Test
    //    void wrongUrl() throws Exception {
    //        McpClient badClient = null;
    //        try {
    //            McpTransport transport = new HttpMcpTransport.Builder()
    //                    .sseUrl("http://localhost:8080/WRONG")
    //                    .logRequests(true)
    //                    .logResponses(true)
    //                    .build();
    //            badClient = new DefaultMcpClient.Builder()
    //                    .transport(transport)
    //                    .toolExecutionTimeout(Duration.ofSeconds(4))
    //                    .build();
    //            fail("Expected an exception");
    //        } catch (Exception e) {
    //            // ok
    //        } finally {
    //            if (badClient != null) {
    //                badClient.close();
    //            }
    //        }
    //    }
}
