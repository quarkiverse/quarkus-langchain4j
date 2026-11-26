package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.BeforeEach;
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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.quarkiverse.langchain4j.cost.AbstractTypeSupportingCacheTokenUsageExtractor;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;
import io.quarkiverse.langchain4j.cost.ReasoningTokenUsageExtractor;
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * Exercises the token usage attributes of the chat model span (listener -> discovered extractors -> span) without a
 * real provider, by firing the wired {@link SpanChatModelListener} with custom {@link TokenUsage} subtypes.
 */
class SpanChatModelListenerTokenUsageTest {

    private static final AttributeKey<Long> INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    private static final AttributeKey<Long> CACHE_READ_INPUT_TOKENS = AttributeKey
            .longKey("gen_ai.usage.cache_read.input_tokens");
    private static final AttributeKey<Long> CACHE_CREATION_INPUT_TOKENS = AttributeKey
            .longKey("gen_ai.usage.cache_creation.input_tokens");
    private static final AttributeKey<Long> REASONING_OUTPUT_TOKENS = AttributeKey
            .longKey("gen_ai.usage.reasoning.output_tokens");

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ListenersProcessorAbstractSpanChatModelListenerTest.appWithInMemorySpanExporter()
                    .addClasses(SeparateReasoningTokenUsage.class, SeparateReasoningTokenUsageExtractor.class,
                            IncludedReasoningTokenUsage.class, IncludedReasoningTokenUsageExtractor.class));

    @Inject
    SpanChatModelListener listener;

    @Inject
    InMemorySpanExporter exporter;

    @BeforeEach
    void cleanupSpans() {
        exporter.reset();
    }

    @Test
    void addsReasoningTokensToOutputTokensWhenReportedSeparately() {
        Attributes attributes = fireAndExport(new SeparateReasoningTokenUsage(100, 50, 7, 40));

        assertThat(attributes.get(INPUT_TOKENS)).isEqualTo(100L);
        assertThat(attributes.get(OUTPUT_TOKENS)).isEqualTo(90L);
        assertThat(attributes.get(REASONING_OUTPUT_TOKENS)).isEqualTo(40L);
        assertThat(attributes.get(CACHE_READ_INPUT_TOKENS)).isEqualTo(7L);
        assertThat(attributes.get(CACHE_CREATION_INPUT_TOKENS)).isNull();
    }

    @Test
    void keepsOutputTokensWhenTheyAlreadyIncludeReasoning() {
        Attributes attributes = fireAndExport(new IncludedReasoningTokenUsage(100, 90, 40));

        assertThat(attributes.get(OUTPUT_TOKENS)).isEqualTo(90L);
        assertThat(attributes.get(REASONING_OUTPUT_TOKENS)).isEqualTo(40L);
    }

    @Test
    void omitsCacheAndReasoningAttributesWhenProviderReportsNone() {
        Attributes attributes = fireAndExport(new SeparateReasoningTokenUsage(100, 50, null, null));

        assertThat(attributes.get(OUTPUT_TOKENS)).isEqualTo(50L);
        assertThat(attributes.get(REASONING_OUTPUT_TOKENS)).isNull();
        assertThat(attributes.get(CACHE_READ_INPUT_TOKENS)).isNull();
    }

    @Test
    void reportsOnlyInputAndOutputTokensForPlainTokenUsage() {
        Attributes attributes = fireAndExport(new TokenUsage(100, 50));

        assertThat(attributes.get(INPUT_TOKENS)).isEqualTo(100L);
        assertThat(attributes.get(OUTPUT_TOKENS)).isEqualTo(50L);
        assertThat(attributes.get(REASONING_OUTPUT_TOKENS)).isNull();
        assertThat(attributes.get(CACHE_READ_INPUT_TOKENS)).isNull();
        assertThat(attributes.get(CACHE_CREATION_INPUT_TOKENS)).isNull();
    }

    private Attributes fireAndExport(TokenUsage tokenUsage) {
        Map<Object, Object> attributes = new HashMap<>();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("hello")))
                .parameters(DefaultChatRequestParameters.builder().modelName("test-model").build())
                .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("hi"))
                .tokenUsage(tokenUsage)
                .build();

        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OTHER, attributes));
        listener.onResponse(new ChatModelResponseContext(response, request, ModelProvider.OTHER, attributes));

        await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(1));
        return exporter.getFinishedSpanItems().get(0).getAttributes();
    }

    static final class SeparateReasoningTokenUsage extends TokenUsage {

        private final Integer cachedTokens;
        private final Integer reasoningTokens;

        SeparateReasoningTokenUsage(Integer inputTokens, Integer outputTokens, Integer cachedTokens,
                Integer reasoningTokens) {
            super(inputTokens, outputTokens);
            this.cachedTokens = cachedTokens;
            this.reasoningTokens = reasoningTokens;
        }
    }

    @Singleton
    static final class SeparateReasoningTokenUsageExtractor
            extends AbstractTypeSupportingCacheTokenUsageExtractor<SeparateReasoningTokenUsage>
            implements ReasoningTokenUsageExtractor {

        @Override
        public CacheTokenUsage extract(TokenUsage tokenUsage) {
            return new CacheTokenUsage(((SeparateReasoningTokenUsage) tokenUsage).cachedTokens, null);
        }

        @Override
        public Integer reasoningOutputTokens(TokenUsage tokenUsage) {
            return ((SeparateReasoningTokenUsage) tokenUsage).reasoningTokens;
        }

        @Override
        public boolean outputTokenCountIncludesReasoning() {
            return false;
        }
    }

    static final class IncludedReasoningTokenUsage extends TokenUsage {

        private final Integer reasoningTokens;

        IncludedReasoningTokenUsage(Integer inputTokens, Integer outputTokens, Integer reasoningTokens) {
            super(inputTokens, outputTokens);
            this.reasoningTokens = reasoningTokens;
        }
    }

    @Singleton
    static final class IncludedReasoningTokenUsageExtractor implements ReasoningTokenUsageExtractor {

        @Override
        public boolean supports(TokenUsage tokenUsage) {
            return tokenUsage instanceof IncludedReasoningTokenUsage;
        }

        @Override
        public Integer reasoningOutputTokens(TokenUsage tokenUsage) {
            return ((IncludedReasoningTokenUsage) tokenUsage).reasoningTokens;
        }
    }
}
