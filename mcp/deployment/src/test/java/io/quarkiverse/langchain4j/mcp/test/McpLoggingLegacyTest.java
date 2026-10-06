package io.quarkiverse.langchain4j.mcp.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.logging.McpLogLevel;
import dev.langchain4j.mcp.client.logging.McpLogMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.test.mock.LegacyMcpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpMockServer;
import io.quarkiverse.langchain4j.mcp.test.mock.McpTool;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Executes a tool that sends a log message to the client on the SSE stream of the tool call
 * response, and verifies that a CDI event was fired with the log message.
 * <p>
 * There is no counterpart for the modern protocol, because the client doesn't request log messages
 * there (servers may only send them for requests carrying {@code io.modelcontextprotocol/logLevel}
 * in {@code _meta}), and logging is deprecated as of 2026-07-28.
 */
public class McpLoggingLegacyTest extends WiremockAware {

    private static final String MCP_PATH = "/mcp/logging-legacy";

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(LogMessageCollector.class)
                    .addPackage(McpMockServer.class.getPackage())
                    .addAsResource(new StringAsset("""
                            quarkus.langchain4j.openai.api-key=whatever
                            quarkus.langchain4j.mcp.client1.transport-type=streamable-http
                            quarkus.langchain4j.mcp.client1.protocol-version=%s
                            quarkus.langchain4j.mcp.client1.url=%s
                            quarkus.langchain4j.mcp.client1.log-requests=true
                            quarkus.langchain4j.mcp.client1.log-responses=true
                            quarkus.log.category."dev.langchain4j".level=DEBUG
                            quarkus.log.category."io.quarkiverse".level=DEBUG
                            """.formatted(LegacyMcpMockServer.PROTOCOL_VERSION, wiremockUrlForConfig(MCP_PATH))),
                            "application.properties"));

    @Inject
    ToolProvider toolProvider;

    @Inject
    LogMessageCollector logMessageCollector;

    @BeforeEach
    void setUpMcpServer() {
        McpMockServer.legacy(wiremock(), MCP_PATH)
                .reset()
                .stubInitialization()
                .stubTools(McpTool.LOGGING)
                .stubToolCallWithLogMessage("logging", "info", "mock-mcp", "This is a log message", "OK");
    }

    @Test
    public void logging() {
        ToolProviderResult toolProviderResult = toolProvider.provideTools(null);
        ToolExecutor executor = toolProviderResult.tools().entrySet().stream()
                .filter(entry -> entry.getKey().name().equals("logging"))
                .findFirst()
                .orElseThrow()
                .getValue();
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("logging")
                .arguments("{}")
                .build();
        assertThat(executor.execute(toolExecutionRequest, null)).isEqualTo("OK");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> logMessageCollector.client1Message() != null);
        McpLogMessage logMessage = logMessageCollector.client1Message();
        assertThat(logMessage.level()).isEqualTo(McpLogLevel.INFO);
        assertThat(logMessage.logger()).isEqualTo("mock-mcp");
        assertThat(logMessage.dataAsMap()).containsEntry("message", "This is a log message");

        // no client named 'client2' actually exists, so no CDI event with this qualifier should be fired
        assertThat(logMessageCollector.client2Message()).isNull();
    }

    @ApplicationScoped
    public static class LogMessageCollector {

        private volatile McpLogMessage client1Message;
        private volatile McpLogMessage client2Message;

        McpLogMessage client1Message() {
            return client1Message;
        }

        McpLogMessage client2Message() {
            return client2Message;
        }

        void onLogMessageClient1(@Observes @McpClientName("client1") McpLogMessage logMessage) {
            client1Message = logMessage;
        }

        void onLogMessageClient2(@Observes @McpClientName("client2") McpLogMessage logMessage) {
            client2Message = logMessage;
        }
    }
}
