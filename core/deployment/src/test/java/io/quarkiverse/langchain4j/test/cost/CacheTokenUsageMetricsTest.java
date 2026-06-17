package io.quarkiverse.langchain4j.test.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsageExtractor;
import io.quarkiverse.langchain4j.runtime.listeners.MetricsChatModelListener;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Exercises the full metrics chain (listener -> resolver -> discovered extractor -> counters) without a real
 * provider, by firing the wired {@link MetricsChatModelListener} with a custom {@link TokenUsage} subtype.
 * <p>
 * TODO: convert to a native {@code @QuarkusIntegrationTest} via the OpenAI integration-tests module once an OpenAI
 * {@code CacheTokenUsageExtractor} exists, to also cover native.
 */
class CacheTokenUsageMetricsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TestProviderTokenUsage.class, TestProviderCacheTokenUsageExtractor.class));

    @Inject
    MetricsChatModelListener listener;

    @Inject
    MeterRegistry registry;

    @BeforeAll
    static void addSimpleRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @Test
    void recordsCacheReadAndCacheCreationTokenCounters() {
        Map<Object, Object> attributes = new HashMap<>();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("hello")))
                .parameters(DefaultChatRequestParameters.builder().modelName("test-model").build())
                .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("hi"))
                .modelName("test-model")
                .tokenUsage(new TestProviderTokenUsage(30, 12))
                .build();

        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OTHER, attributes));
        listener.onResponse(new ChatModelResponseContext(response, request, ModelProvider.OTHER, attributes));

        Counter cacheRead = registry.find("gen_ai.client.token.usage")
                .tag("gen_ai.token.type", "cache_read")
                .counter();
        Counter cacheCreation = registry.find("gen_ai.client.token.usage")
                .tag("gen_ai.token.type", "cache_creation")
                .counter();

        assertThat(cacheRead).isNotNull();
        assertThat(cacheRead.count()).isEqualTo(30.0);
        assertThat(cacheCreation).isNotNull();
        assertThat(cacheCreation.count()).isEqualTo(12.0);
    }

    static final class TestProviderTokenUsage extends TokenUsage {

        private final Integer cacheReadInputTokens;
        private final Integer cacheCreationInputTokens;

        TestProviderTokenUsage(Integer cacheReadInputTokens, Integer cacheCreationInputTokens) {
            super(10, 20);
            this.cacheReadInputTokens = cacheReadInputTokens;
            this.cacheCreationInputTokens = cacheCreationInputTokens;
        }

        Integer cacheReadInputTokens() {
            return cacheReadInputTokens;
        }

        Integer cacheCreationInputTokens() {
            return cacheCreationInputTokens;
        }
    }

    @Singleton
    static final class TestProviderCacheTokenUsageExtractor implements CacheTokenUsageExtractor {

        @Override
        public boolean supports(TokenUsage tokenUsage) {
            return tokenUsage instanceof TestProviderTokenUsage;
        }

        @Override
        public CacheTokenUsage extract(TokenUsage tokenUsage) {
            TestProviderTokenUsage usage = (TestProviderTokenUsage) tokenUsage;
            return new CacheTokenUsage(usage.cacheReadInputTokens(), usage.cacheCreationInputTokens());
        }
    }
}
