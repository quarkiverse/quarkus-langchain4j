package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.McpResourceUpdatedEvent;
import io.quarkus.test.QuarkusUnitTest;

class McpResourceSubscriptionStdioTest extends McpResourceSubscriptionTestBase {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "stdio")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.command",
                    "jbang,--quiet,--fresh,run,src/test/resources/resource_subscriptions_mcp_server.java")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    @Inject
    @McpClientName("client1")
    McpClient injectedMcpClient;

    public void onResourceUpdated(@Observes @McpClientName("client1") McpResourceUpdatedEvent event) {
        updatedResourceUris.add(event.uri());
    }

    @BeforeAll
    static void setup() throws Exception {
        copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready("resource_subscriptions_mcp_server.java");
        skipTestsIfJbangNotAvailable();
    }

    @BeforeEach
    void init() {
        mcpClient = injectedMcpClient;
    }
}
