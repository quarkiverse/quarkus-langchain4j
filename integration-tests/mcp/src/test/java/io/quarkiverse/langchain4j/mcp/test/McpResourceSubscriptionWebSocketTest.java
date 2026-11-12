package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.startServerHttp;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.McpResourceUpdatedEvent;
import io.quarkus.test.QuarkusExtensionTest;

class McpResourceSubscriptionWebSocketTest extends McpResourceSubscriptionTestBase {

    private static Process process;

    @RegisterExtension
    static QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "websocket")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.url", "ws://localhost:8189/mcp/ws")
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
        skipTestsIfJbangNotAvailable();
        process = startServerHttp("resource_subscriptions_mcp_server.java", 8189);
    }

    @BeforeEach
    void init() {
        mcpClient = injectedMcpClient;
    }

    @AfterAll
    static void teardown() throws Exception {
        if (process != null && process.isAlive()) {
            McpServerHelper.destroyProcessTree(process);
        }
    }

    /**
     * Quarkus MCP Server (up to at least 2.0.x) ignores the {@code notifications/cancelled} sent by the client to
     * unsubscribe over WebSocket with MCP 2026-07-28: each stateless message is handled on a transient connection,
     * and the server only looks up the subscription on other connections for the stdio transport. The subscription
     * stays active and notifications keep arriving after unsubscribing.
     */
    @Override
    @Test
    @Disabled("Quarkus MCP Server does not cancel subscriptions over WebSocket on MCP 2026-07-28")
    public void unsubscribeStopsNotifications() {
        super.unsubscribeStopsNotifications();
    }
}
