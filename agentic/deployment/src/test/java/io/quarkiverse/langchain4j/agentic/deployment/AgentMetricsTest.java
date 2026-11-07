package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

public class AgentMetricsTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SimpleAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    public interface SimpleAgent {

        @UserMessage("Answer the following question: {{request}}")
        @Agent(description = "A simple agent for testing")
        String ask(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return chatModel;
        }
    }

    @Inject
    SimpleAgent agent;

    private static final ChatModel chatModel = new ChatModel() {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder()
                    .aiMessage(new AiMessage("test response"))
                    .tokenUsage(new TokenUsage(9, 12))
                    .build();
        }

        @Override
        public List<ChatModelListener> listeners() {
            return Arc.container().select(ChatModelListener.class)
                    .stream().collect(Collectors.toList());
        }
    };

    @Test
    void tokenUsageMetricsFromAgentAndDirectCallHaveConsistentTagKeys() {
        setChatCompletionMessageContent("test response");

        agent.ask("question via agent");
        chatModel.chat("question via direct call");

        Collection<Counter> counters = Metrics.globalRegistry.find("gen_ai.client.token.usage").counters();
        assertThat(counters).isNotEmpty();

        assertAllMetersShareSameTagKeys(counters);
        assertTagKeysPresentOnAll(counters,
                "ai_service.class_name", "ai_service.method_name", "error.type",
                "gen_ai.request.model", "gen_ai.response.model");
    }

    @Test
    void durationMetricsFromAgentAndDirectCallHaveConsistentTagKeys() {
        setChatCompletionMessageContent("test response");

        agent.ask("question via agent");
        chatModel.chat("question via direct call");

        Collection<Timer> timers = Metrics.globalRegistry.find("gen_ai.client.operation.duration").timers();
        assertThat(timers).isNotEmpty();

        assertAllMetersShareSameTagKeys(timers);
        assertTagKeysPresentOnAll(timers,
                "ai_service.class_name", "ai_service.method_name", "error.type",
                "gen_ai.request.model", "gen_ai.response.model");
    }

    private static void assertAllMetersShareSameTagKeys(Collection<? extends Meter> meters) {
        Set<Set<String>> distinctTagKeySets = meters.stream()
                .map(meter -> meter.getId().getTags().stream()
                        .map(Tag::getKey)
                        .collect(Collectors.toSet()))
                .collect(Collectors.toSet());

        assertThat(distinctTagKeySets)
                .as("All meters with the same name must have identical tag keys (Prometheus requirement)")
                .hasSize(1);
    }

    private static void assertTagKeysPresentOnAll(Collection<? extends Meter> meters,
            String... expectedKeys) {
        for (Meter meter : meters) {
            Set<String> actualKeys = meter.getId().getTags().stream()
                    .map(Tag::getKey)
                    .collect(Collectors.toSet());
            assertThat(actualKeys)
                    .as("Meter %s should contain all expected tag keys", meter.getId())
                    .contains(expectedKeys);
        }
    }
}
