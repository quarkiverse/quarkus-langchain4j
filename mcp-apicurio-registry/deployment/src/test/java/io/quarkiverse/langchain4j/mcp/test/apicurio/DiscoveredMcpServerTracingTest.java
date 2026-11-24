package io.quarkiverse.langchain4j.mcp.test.apicurio;

import static io.quarkiverse.langchain4j.mcp.test.apicurio.FakeRegistryAndMcpServer.ARTIFACT_ID;
import static io.quarkiverse.langchain4j.mcp.test.apicurio.FakeRegistryAndMcpServer.PORT;
import static io.quarkiverse.langchain4j.mcp.test.apicurio.FakeRegistryAndMcpServer.RECEIVED_TRACEPARENTS;
import static io.quarkiverse.langchain4j.mcp.test.apicurio.FakeRegistryAndMcpServer.SERVER_KEY;
import static io.quarkiverse.langchain4j.mcp.test.apicurio.FakeRegistryAndMcpServer.TOOL_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that an MCP server discovered through Apicurio Registry is traced like a configured MCP client:
 * the tool call gets an MCP client span, and its trace context reaches the server in the request {@code _meta}.
 */
public class DiscoveredMcpServerTracingTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(FakeRegistryAndMcpServer.class, FakeRegistryAndMcpServer.FakeRegistry.class,
                            FakeRegistryAndMcpServer.FakeMcpServer.class,
                            FakeRegistryAndMcpServer.InMemorySpanExporterProducer.class))
            .overrideConfigKey("quarkus.http.test-port", String.valueOf(PORT))
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.url",
                    "http://localhost:" + PORT + "/apis/registry/v3")
            .overrideConfigKey("quarkus.otel.traces.sampler", "always_on")
            .overrideConfigKey("quarkus.otel.bsp.schedule.delay", "PT0.001S")
            .overrideConfigKey("quarkus.otel.bsp.max.queue.size", "1")
            .overrideConfigKey("quarkus.otel.bsp.max.export.batch.size", "1");

    @Inject
    ApicurioRegistryMcpTools apicurioRegistryMcpTools;

    @Inject
    InMemorySpanExporter spanExporter;

    @Test
    void toolCallIsTracedAndPropagatesTraceContext() {
        assertThat(apicurioRegistryMcpTools.connectMcpServer(ARTIFACT_ID, "default"))
                .startsWith("Connected to MCP server '" + SERVER_KEY + "'");
        RECEIVED_TRACEPARENTS.clear();
        spanExporter.reset();

        assertThat(apicurioRegistryMcpTools.callMcpTool(SERVER_KEY, TOOL_NAME, "{}")).isEqualTo("echoed");

        String spanName = "tools/call " + TOOL_NAME;
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(spanExporter.getFinishedSpanItems())
                .anyMatch(span -> span.getName().equals(spanName)));
        SpanData span = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(spanName))
                .findFirst()
                .orElseThrow();
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);

        assertThat(RECEIVED_TRACEPARENTS).hasSize(1);
        String traceparent = RECEIVED_TRACEPARENTS.get(0);
        assertThat(traceparent).startsWith("00-" + span.getTraceId() + "-" + span.getSpanId() + "-");
    }
}
