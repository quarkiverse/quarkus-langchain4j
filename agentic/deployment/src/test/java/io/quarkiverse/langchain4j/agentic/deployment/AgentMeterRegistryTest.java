package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
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
import dev.langchain4j.service.V;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Reproducer for <a href="https://github.com/quarkiverse/quarkus-langchain4j/issues/2349">#2349</a>:
 * MetricsChatModelListener registers gen_ai.client.token.usage with inconsistent tag sets
 * when both @Agent and @RegisterAiService are used in the same application.
 */
public class AgentMeterRegistryTest extends OpenAiBaseTest {

    @BeforeAll
    static void addSimpleRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SimpleAgent.class, SimpleAiService.class, TestChatModelSupplier.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    private static final ChatModel testChatModel = new ChatModel() {
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

    public interface SimpleAgent {

        @Agent(description = "A simple test agent")
        String ask(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return testChatModel;
        }
    }

    public static class TestChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return testChatModel;
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = TestChatModelSupplier.class)
    public interface SimpleAiService {

        String chat(String message);
    }

    @Inject
    MeterRegistry registry;

    @Inject
    SimpleAgent agent;

    @Inject
    SimpleAiService aiService;

    @Inject
    Vertx vertx;

    @Test
    void agentAndAiServiceTokenUsageMetricsHaveConsistentTagKeys() throws Exception {
        agent.ask("test question");

        assertThat(registry.find("gen_ai.client.token.usage").counters())
                .as("Agent call should have produced token usage metrics")
                .isNotEmpty();

        Context dupCtx = VertxContext.createNewDuplicatedContext(vertx.getOrCreateContext());
        CompletableFuture<Void> future = new CompletableFuture<>();

        dupCtx.executeBlocking(() -> {
            ManagedContext requestContext = Arc.container().requestContext();
            requestContext.activate();
            try {
                aiService.chat("hello");
            } finally {
                requestContext.terminate();
            }
            return null;
        }).onComplete(ar -> {
            if (ar.failed()) {
                future.completeExceptionally(ar.cause());
            } else {
                future.complete(null);
            }
        });

        future.get(10, TimeUnit.SECONDS);

        // After both calls, all gen_ai.client.token.usage counters must share the same
        // tag keys — Prometheus rejects meters with inconsistent tag key sets.
        // Before the fix for #2349, the agent produced counters WITHOUT ai_service tags
        // and the AI service produced counters WITH them, causing silent metric loss.
        var counters = registry.find("gen_ai.client.token.usage").counters();
        assertThat(counters).hasSizeGreaterThanOrEqualTo(4);

        Set<Set<String>> distinctTagKeySets = counters.stream()
                .map(c -> c.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet()))
                .collect(Collectors.toSet());

        assertThat(distinctTagKeySets)
                .as("All gen_ai.client.token.usage counters must have identical tag keys")
                .hasSize(1);

        assertThat(distinctTagKeySets.iterator().next())
                .as("Tag keys should include ai_service.class_name and ai_service.method_name")
                .contains("ai_service.class_name", "ai_service.method_name");

        Counter aiServiceCounter = registry.get("gen_ai.client.token.usage")
                .tag("gen_ai.token.type", "input")
                .tag("ai_service.class_name",
                        "io.quarkiverse.langchain4j.agentic.deployment.AgentMeterRegistryTest$SimpleAiService")
                .counter();
        assertThat(aiServiceCounter.count()).isGreaterThan(0);
    }
}
