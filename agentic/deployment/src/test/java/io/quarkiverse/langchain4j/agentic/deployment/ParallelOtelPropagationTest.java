package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that OTel trace context is propagated to parallel agent worker threads.
 * When ManagedExecutor is wired as the default executor, spans created on worker
 * threads should share the same trace ID as the parent span on the calling thread.
 */
public class ParallelOtelPropagationTest extends OpenAiBaseTest {

    private static final String APPLICATION_PROPERTIES = """
            quarkus.otel.bsp.schedule.delay=PT0.001S
            quarkus.otel.bsp.max.queue.size=1
            quarkus.otel.bsp.max.export.batch.size=1
            """;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(OtelSubAgentA.class, OtelSubAgentB.class, OtelParallelAgent.class,
                            InMemorySpanExporterProducer.class, Agents.FixedResponseChatModel.class)
                    .addAsResource(new StringAsset(APPLICATION_PROPERTIES), "application.properties"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "test-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface OtelSubAgentA {
        @UserMessage("A: {{input}}")
        @Agent(description = "Sub-agent A", outputKey = "a")
        String process(@V("input") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("result-a");
        }
    }

    public interface OtelSubAgentB {
        @UserMessage("B: {{input}}")
        @Agent(description = "Sub-agent B", outputKey = "b")
        String process(@V("input") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("result-b");
        }
    }

    public interface OtelParallelAgent {
        @ParallelAgent(outputKey = "result", subAgents = { OtelSubAgentA.class, OtelSubAgentB.class })
        String run(@V("input") String input);
    }

    @ApplicationScoped
    public static class InMemorySpanExporterProducer {
        @Produces
        @ApplicationScoped
        InMemorySpanExporter exporter() {
            return InMemorySpanExporter.create();
        }
    }

    @Inject
    OtelParallelAgent agent;

    @Inject
    InMemorySpanExporter spanExporter;

    @Inject
    Tracer tracer;

    @BeforeEach
    void resetSpans() {
        spanExporter.reset();
    }

    @Test
    void parallelSubAgentsShareParentTraceId() {
        Span parentSpan = tracer.spanBuilder("test-parent").startSpan();
        try (var scope = parentSpan.makeCurrent()) {
            agent.run("otel-test");
        } finally {
            parentSpan.end();
        }

        // Wait for the batch span processor to export the parent span
        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(spanExporter.getFinishedSpanItems())
                        .anyMatch(s -> s.getName().equals("test-parent")));

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        String parentTraceId = spans.stream()
                .filter(s -> s.getName().equals("test-parent"))
                .map(SpanData::getTraceId)
                .findFirst()
                .orElse(null);

        assertThat(parentTraceId).as("Parent span should exist").isNotNull();

        // All spans in the trace (parent + any child spans from sub-agents)
        List<SpanData> traceSpans = spans.stream()
                .filter(s -> s.getTraceId().equals(parentTraceId))
                .toList();

        assertThat(traceSpans.size()).as("Should have parent + child spans in same trace").isGreaterThan(1);
    }
}
