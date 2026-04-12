package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.McpResourceUpdatedEvent;
import io.quarkus.test.QuarkusUnitTest;

class McpResourceSubscriptionStdioTransportTest {

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
    McpClient mcpClient;

    static final List<McpResourceUpdatedEvent> receivedEvents = new CopyOnWriteArrayList<>();

    public void onResourceUpdated(@Observes @McpClientName("client1") McpResourceUpdatedEvent event) {
        receivedEvents.add(event);
    }

    @BeforeAll
    static void setup() throws Exception {
        copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready("resource_subscriptions_mcp_server.java");
        skipTestsIfJbangNotAvailable();
    }

    @Test
    public void subscribeAndReceiveResourceUpdate() {
        receivedEvents.clear();

        // subscribe to the status resource
        mcpClient.subscribeToResource("file:///status");

        // verify initial value
        McpReadResourceResult initialResult = mcpClient.readResource("file:///status");
        assertThat(((McpTextResourceContents) initialResult.contents().get(0)).text())
                .isEqualTo("initial");

        // update the resource on the server via a tool call
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"updated\"}")
                .build());

        // wait for the notification to arrive via the CDI event
        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    assertThat(receivedEvents).anySatisfy(event -> {
                        assertThat(event.uri()).isEqualTo("file:///status");
                        assertThat(event.mcpClientKey()).isEqualTo("client1");
                    });
                });

        // re-read the resource to verify the content changed
        McpReadResourceResult updatedResult = mcpClient.readResource("file:///status");
        assertThat(((McpTextResourceContents) updatedResult.contents().get(0)).text())
                .isEqualTo("updated");

        // unsubscribe
        mcpClient.unsubscribeFromResource("file:///status");

        // clear the list and update again
        receivedEvents.clear();
        mcpClient.executeTool(ToolExecutionRequest.builder()
                .name("updateStatus")
                .arguments("{\"newValue\": \"updated-again\"}")
                .build());

        // wait and verify no notification was received after unsubscribing
        Awaitility.await()
                .during(Duration.ofSeconds(4))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> assertThat(receivedEvents).isEmpty());
    }

}
