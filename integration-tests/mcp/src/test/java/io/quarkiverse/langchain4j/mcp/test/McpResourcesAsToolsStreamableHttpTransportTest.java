package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "QUARKUS_LANGCHAIN4J_OPENAI_API_KEY", matches = ".+")
class McpResourcesAsToolsStreamableHttpTransportTest extends McpResourcesAsToolsTestBase {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.alice.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.alice.url",
                    "http://localhost:8180/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.alice.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.alice.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.bob.transport-type", "streamable-http")
            .overrideConfigKey("quarkus.langchain4j.mcp.bob.url",
                    "http://localhost:8181/mcp")
            .overrideConfigKey("quarkus.langchain4j.mcp.bob.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.bob.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.expose-resources-as-tools", "true")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    private static Process processAlice;
    private static Process processBob;

    @BeforeAll
    static void setup() throws Exception {
        skipTestsIfJbangNotAvailable();
        processAlice = startServerHttp("resources_alice_mcp_server.java", 8180);
        processBob = startServerHttp("resources_bob_mcp_server.java", 8181);
    }

    @AfterAll
    static void teardown() throws Exception {
        if (processAlice != null && processAlice.isAlive()) {
            processAlice.destroyForcibly();
        }
        if (processBob != null && processBob.isAlive()) {
            processBob.destroyForcibly();
        }
    }
}
