package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "QUARKUS_LANGCHAIN4J_OPENAI_API_KEY", matches = ".+")
class McpResourcesAsToolsStdioTransportTest extends McpResourcesAsToolsTestBase {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.alice.transport-type", "stdio")
            .overrideConfigKey("quarkus.langchain4j.mcp.alice.command",
                    "jbang,--quiet,--fresh,run,-Dquarkus.http.port=7777,src/test/resources/resources_alice_mcp_server.java")
            .overrideConfigKey("quarkus.langchain4j.mcp.alice.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.alice.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.bob.transport-type", "stdio")
            .overrideConfigKey("quarkus.langchain4j.mcp.bob.command",
                    "jbang,--quiet,--fresh,run,-Dquarkus.http.port=7778,src/test/resources/resources_bob_mcp_server.java")
            .overrideConfigKey("quarkus.langchain4j.mcp.bob.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.bob.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.expose-resources-as-tools", "true")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    @BeforeAll
    static void setup() {
        copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready("resources_alice_mcp_server.java");
        copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready("resources_bob_mcp_server.java");
        skipTestsIfJbangNotAvailable();
    }

}
