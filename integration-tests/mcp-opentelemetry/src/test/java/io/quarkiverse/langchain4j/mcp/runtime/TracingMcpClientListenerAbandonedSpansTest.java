package io.quarkiverse.langchain4j.mcp.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.protocol.McpCallToolRequest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpClientRuntimeConfig;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpTransportType;

/**
 * Tests that the tracing listener ends the spans of the operations whose end the MCP client never reports.
 */
public class TracingMcpClientListenerAbandonedSpansTest {

    static final Duration MAX_SPAN_AGE = Duration.ofMillis(50);

    final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    final SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    final TracingMcpClientListener listener = new TracingMcpClientListener(tracerProvider.get("test"),
            TextMapPropagator.noop(), McpTransportType.STDIO, null, null, MAX_SPAN_AGE);

    @AfterEach
    void close() {
        tracerProvider.close();
    }

    @Test
    public void abandonedSpanIsEndedWhenTheNextOperationStarts() throws InterruptedException {
        listener.beforeExecuteTool(toolCall(1, "abandoned"));
        Thread.sleep(MAX_SPAN_AGE.multipliedBy(2).toMillis());

        McpCallContext next = toolCall(2, "next");
        listener.beforeExecuteTool(next);

        SpanData abandoned = span("tools/call abandoned");
        assertThat(abandoned.hasEnded()).isTrue();
        assertThat(abandoned.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(abandoned.getStatus().getDescription()).isEqualTo("The MCP client did not report the end of the operation");
        assertThat(abandoned.getAttributes().get(AttributeKey.stringKey("error.type"))).isEqualTo("_OTHER");

        // the operation that started the check is not affected
        listener.afterExecuteTool(next, null, null);
        assertThat(span("tools/call next").getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
    }

    @Test
    public void lateEndOfAnAbandonedOperationIsIgnored() throws InterruptedException {
        McpCallContext abandoned = toolCall(1, "abandoned");
        listener.beforeExecuteTool(abandoned);
        Thread.sleep(MAX_SPAN_AGE.multipliedBy(2).toMillis());
        listener.beforeExecuteTool(toolCall(2, "next"));

        listener.afterExecuteTool(abandoned, null, null);

        assertThat(exporter.getFinishedSpanItems()).filteredOn(s -> s.getName().equals("tools/call abandoned"))
                .hasSize(1)
                .allSatisfy(s -> assertThat(s.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR));
    }

    @Test
    public void spanInProgressForLessThanTheMaxAgeIsNotEnded() {
        TracingMcpClientListener patientListener = new TracingMcpClientListener(tracerProvider.get("test"),
                TextMapPropagator.noop(), McpTransportType.STDIO, null, null, Duration.ofHours(1));

        patientListener.beforeExecuteTool(toolCall(1, "slow"));
        patientListener.beforeExecuteTool(toolCall(2, "next"));

        assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }

    @Test
    public void maxSpanAgeCoversTheLongestTimeoutWithEveryMultiRoundTripRetry() {
        assertThat(McpRecorder.tracingMaxSpanAge(config(Duration.ofSeconds(30), Duration.ofSeconds(10))))
                // the 60s default timeout of prompts, 4 times (one request and 3 retries), plus a minute
                .isEqualTo(Duration.ofMinutes(5));
        assertThat(McpRecorder.tracingMaxSpanAge(config(Duration.ofMinutes(10), Duration.ofSeconds(10))))
                .isEqualTo(Duration.ofMinutes(41));
        assertThat(McpRecorder.tracingMaxSpanAge(config(Duration.ZERO, Duration.ofSeconds(10))))
                .isEqualTo(Duration.ofHours(1));
    }

    private SpanData span(String name) {
        List<SpanData> spans = exporter.getFinishedSpanItems();
        return spans.stream().filter(s -> s.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No span named " + name + " in " + spans));
    }

    private static McpCallContext toolCall(long id, String toolName) {
        return new McpCallContext(null, new McpCallToolRequest(id, toolName, Map.of()));
    }

    private static McpClientRuntimeConfig config(Duration toolExecutionTimeout, Duration resourcesTimeout) {
        return (McpClientRuntimeConfig) Proxy.newProxyInstance(McpClientRuntimeConfig.class.getClassLoader(),
                new Class<?>[] { McpClientRuntimeConfig.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "toolExecutionTimeout" -> toolExecutionTimeout;
                    case "resourcesTimeout" -> resourcesTimeout;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
