package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.test.QuarkusUnitTest;

public class ParallelOtelPropagationTest {

    static final List<Thread> workerThreads = new CopyOnWriteArrayList<>();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(OtelSubAgentA.class, OtelSubAgentB.class, OtelParallelAgent.class,
                            TraceCapturingChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "unused")
            .overrideConfigKey("quarkus.otel.traces.exporter", "none");

    public interface OtelSubAgentA {
        @UserMessage("A: {{input}}")
        @Agent(outputKey = "a")
        String process(@V("input") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new TraceCapturingChatModel();
        }
    }

    public interface OtelSubAgentB {
        @UserMessage("B: {{input}}")
        @Agent(outputKey = "b")
        String process(@V("input") String input);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new TraceCapturingChatModel();
        }
    }

    public interface OtelParallelAgent {
        @ParallelAgent(outputKey = "result", subAgents = { OtelSubAgentA.class, OtelSubAgentB.class })
        List<String> run(@V("input") String input);

        @Output
        static List<String> output(@V("a") String a, @V("b") String b) {
            return List.of(a, b);
        }
    }

    public static class TraceCapturingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            workerThreads.add(Thread.currentThread());
            String traceId = Span.current().getSpanContext().getTraceId();
            return ChatResponse.builder().aiMessage(AiMessage.from(traceId)).build();
        }
    }

    @Inject
    OtelParallelAgent agent;

    @Inject
    Tracer tracer;

    @Test
    void parallelSubAgentsShareParentTraceId() {
        workerThreads.clear();
        Span parentSpan = tracer.spanBuilder("test-parent").startSpan();
        try (var scope = parentSpan.makeCurrent()) {
            assertThat(parentSpan.getSpanContext().isValid()).isTrue();
            String traceId = parentSpan.getSpanContext().getTraceId();
            assertThat(agent.run("otel-test")).containsExactly(traceId, traceId);
            assertThat(workerThreads).hasSize(2)
                    .allSatisfy(thread -> assertThat(thread).isNotSameAs(Thread.currentThread()));
        } finally {
            parentSpan.end();
        }
    }
}
