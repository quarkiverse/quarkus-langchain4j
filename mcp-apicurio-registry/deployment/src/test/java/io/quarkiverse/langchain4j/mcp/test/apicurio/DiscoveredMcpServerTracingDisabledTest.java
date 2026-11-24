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

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Verifies that {@code quarkus.langchain4j.mcp.tracing.enabled=false} also applies to MCP servers discovered
 * through Apicurio Registry: no MCP client span, and no trace context sent to the server.
 */
public class DiscoveredMcpServerTracingDisabledTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(FakeRegistryAndMcpServer.class, FakeRegistryAndMcpServer.FakeRegistry.class,
                            FakeRegistryAndMcpServer.FakeMcpServer.class,
                            FakeRegistryAndMcpServer.InMemorySpanExporterProducer.class))
            .overrideConfigKey("quarkus.http.test-port", String.valueOf(PORT))
            .overrideConfigKey("quarkus.langchain4j.mcp.apicurio-registry.url",
                    "http://localhost:" + PORT + "/apis/registry/v3")
            .overrideConfigKey("quarkus.langchain4j.mcp.tracing.enabled", "false")
            .overrideConfigKey("quarkus.otel.traces.sampler", "always_on")
            .overrideConfigKey("quarkus.otel.bsp.schedule.delay", "PT0.001S")
            .overrideConfigKey("quarkus.otel.bsp.max.queue.size", "1")
            .overrideConfigKey("quarkus.otel.bsp.max.export.batch.size", "1");

    @Inject
    ApicurioRegistryMcpTools apicurioRegistryMcpTools;

    @Inject
    InMemorySpanExporter spanExporter;

    @Test
    void toolCallIsNotTracedAndSendsNoTraceContext() {
        assertThat(apicurioRegistryMcpTools.connectMcpServer(ARTIFACT_ID, "default"))
                .startsWith("Connected to MCP server '" + SERVER_KEY + "'");
        RECEIVED_TRACEPARENTS.clear();
        spanExporter.reset();

        assertThat(apicurioRegistryMcpTools.callMcpTool(SERVER_KEY, TOOL_NAME, "{}")).isEqualTo("echoed");

        assertThat(RECEIVED_TRACEPARENTS).containsExactly("none");
        // the fake server's own HTTP server spans are expected, MCP client spans are not
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(spanExporter.getFinishedSpanItems())
                        .noneMatch(span -> span.getName().startsWith("tools/call")));
    }
}
