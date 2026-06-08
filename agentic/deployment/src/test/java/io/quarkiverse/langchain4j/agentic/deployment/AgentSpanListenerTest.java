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
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AgentSpanListenerTest extends OpenAiBaseTest {

    private static final String APPLICATION_PROPERTIES = """
            quarkus.otel.bsp.schedule.delay=PT0.001S
            quarkus.otel.bsp.max.queue.size=1
            quarkus.otel.bsp.max.export.batch.size=1
            """;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SpanTestAgent.class, InMemorySpanExporterProducer.class,
                            Agents.FixedResponseChatModel.class)
                    .addAsResource(new StringAsset(APPLICATION_PROPERTIES), "application.properties"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface SpanTestAgent {
        @UserMessage("Answer: {{request}}")
        @Agent(description = "span test agent")
        String ask(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("span-response");
        }
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
    SpanTestAgent agent;

    @Inject
    InMemorySpanExporter spanExporter;

    @BeforeEach
    void resetSpans() {
        spanExporter.reset();
    }

    @Test
    void agentInvocationCreatesSpanWithCorrectAttributes() {
        agent.ask("test-question");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            List<SpanData> agentSpans = spans.stream()
                    .filter(s -> s.getName().startsWith("langchain4j.agent."))
                    .toList();
            assertThat(agentSpans).isNotEmpty();
        });

        List<SpanData> agentSpans = spanExporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().startsWith("langchain4j.agent."))
                .toList();

        SpanData agentSpan = agentSpans.get(0);
        assertThat(agentSpan.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")))
                .isEqualTo("agent_invocation");
        assertThat(agentSpan.getAttributes().get(AttributeKey.stringKey("gen_ai.agent.name")))
                .isNotBlank();
        assertThat(agentSpan.getAttributes().get(AttributeKey.stringKey("gen_ai.agent.id")))
                .isNotBlank();
    }
}
