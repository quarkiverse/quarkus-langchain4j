package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class McpTracingStdioTest extends McpTracingModernTestBase {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class, TestSpanExporter.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "stdio")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.protocol-version", "2026-07-28")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.command",
                    "jbang,--quiet,--fresh,run,src/test/resources/tracing_mcp_server.java")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.tool-execution-timeout", "3s")
            // every list operation must reach the server
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.cache-tool-list", "false")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.cache-resource-list", "false")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.cache-prompt-list", "false")
            // health checks are triggered by the tests
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.auto-health-check", "false")
            .overrideConfigKey("quarkus.otel.bsp.schedule.delay", "PT0.001S")
            .overrideConfigKey("quarkus.otel.bsp.max.queue.size", "1000")
            .overrideConfigKey("quarkus.otel.bsp.max.export.batch.size", "1")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    @BeforeAll
    static void setup() throws Exception {
        copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready("tracing_mcp_server.java");
        skipTestsIfJbangNotAvailable();
    }

    @Override
    Integer httpPort() {
        return null;
    }

    @Override
    boolean unsubscribeSendsCancellation() {
        return true;
    }
}
