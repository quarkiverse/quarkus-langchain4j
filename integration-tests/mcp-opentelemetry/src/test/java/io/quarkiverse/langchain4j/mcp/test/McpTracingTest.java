package io.quarkiverse.langchain4j.mcp.test;

import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready;
import static io.quarkiverse.langchain4j.mcp.test.McpServerHelper.skipTestsIfJbangNotAvailable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.McpClient;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.test.QuarkusUnitTest;

public class McpTracingTest {

    static final Duration TIMEOUT = Duration.ofSeconds(30);

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(McpServerHelper.class, InMemorySpanExporterProducer.class))
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.transport-type", "stdio")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.command",
                    "jbang,--quiet,--fresh,run,src/test/resources/tracing_mcp_server.java")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-requests", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.log-responses", "true")
            .overrideConfigKey("quarkus.langchain4j.mcp.client1.tool-execution-timeout", "10s")
            .overrideConfigKey("quarkus.otel.bsp.schedule.delay", "PT0.001S")
            .overrideConfigKey("quarkus.otel.bsp.max.queue.size", "1")
            .overrideConfigKey("quarkus.otel.bsp.max.export.batch.size", "1")
            .overrideConfigKey("quarkus.log.category.\"io.quarkiverse\".level", "DEBUG");

    @Inject
    @McpClientName("client1")
    McpClient mcpClient;

    @Inject
    InMemorySpanExporter spanExporter;

    @BeforeAll
    static void setup() throws Exception {
        copyMcpServerScriptToSrcTestResourcesIfItsNotThereAlready("tracing_mcp_server.java");
        skipTestsIfJbangNotAvailable();
    }

    @BeforeEach
    void clearSpans() {
        spanExporter.reset();
    }

    @Test
    public void executeToolCreatesSpanWithProperAttributes() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("echoMeta")
                .arguments("{\"key\": \"traceparent\"}")
                .build();
        String result = mcpClient.executeTool(request).resultText();

        // Verify that TracingMcpClientListener created a span with the correct name and attributes
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(spanExporter.getFinishedSpanItems())
                .anyMatch(s -> s.getName().equals("execute_tool echoMeta")));

        SpanData toolSpan = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals("execute_tool echoMeta"))
                .findFirst()
                .orElseThrow();

        assertThat(toolSpan.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")))
                .isEqualTo("execute_tool");
        assertThat(toolSpan.getAttributes().get(AttributeKey.stringKey("gen_ai.tool.name")))
                .isEqualTo("echoMeta");

        // Verify trace context was propagated to the MCP server
        assertThat(result).isNotEqualTo("null");
        assertThat(result).startsWith("00-");
        assertThat(result).contains(toolSpan.getTraceId());
    }

    @ApplicationScoped
    public static class InMemorySpanExporterProducer {
        @Produces
        @ApplicationScoped
        InMemorySpanExporter exporter() {
            return InMemorySpanExporter.create();
        }
    }
}
