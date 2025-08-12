package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

@Disabled("With streamable-http, server requesting roots from the client doesn't work atm")
class McpRootsStreamableHttpTransportTest extends McpRootsTestBase {

    private static Process process;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url", "http://localhost:8082/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.roots", "David's workspace=file:///home/david/workspace")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("roots_mcp_server.java");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
