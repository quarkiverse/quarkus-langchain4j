package io.quarkiverse.langchain4j.test.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsageExtractor;
import io.quarkiverse.langchain4j.cost.CacheTokenUsageResolver;
import io.quarkiverse.langchain4j.cost.Cost;
import io.quarkiverse.langchain4j.cost.CostEstimator;
import io.quarkiverse.langchain4j.cost.CostEstimatorService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Proves the cost path of the hybrid design: a {@link CostEstimator} reads prompt cache tokens via the injected
 * {@link CacheTokenUsageResolver} from {@link CostEstimator.SupportsContext#responseContext()}, and
 * {@link CostEstimatorService} folds the cache costs into the total.
 */
class CostEstimatorCacheCostTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(CacheAwareTokenUsage.class, CacheAwareExtractor.class, CacheAwareCostEstimator.class));

    @Inject
    CostEstimatorService costEstimatorService;

    @Test
    void foldsCacheCostsIntoTotal() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("hello")))
                .parameters(DefaultChatRequestParameters.builder().modelName("cache-model").build())
                .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("hi"))
                .modelName("cache-model")
                .tokenUsage(new CacheAwareTokenUsage(100, 50, 30, 12))
                .build();

        Cost cost = costEstimatorService
                .estimate(new ChatModelResponseContext(response, request, ModelProvider.OTHER, new HashMap<>()));

        assertThat(cost).isNotNull();
        assertThat(cost.currencyCode()).isEqualTo("USD");
        assertThat(cost.number()).isEqualByComparingTo("192");
    }

    static final class CacheAwareTokenUsage extends TokenUsage {

        private final Integer cacheReadInputTokens;
        private final Integer cacheCreationInputTokens;

        CacheAwareTokenUsage(int inputTokens, int outputTokens, Integer cacheReadInputTokens,
                Integer cacheCreationInputTokens) {
            super(inputTokens, outputTokens);
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
    static final class CacheAwareExtractor implements CacheTokenUsageExtractor {

        @Override
        public boolean supports(TokenUsage tokenUsage) {
            return tokenUsage instanceof CacheAwareTokenUsage;
        }

        @Override
        public CacheTokenUsage extract(TokenUsage tokenUsage) {
            CacheAwareTokenUsage usage = (CacheAwareTokenUsage) tokenUsage;
            return new CacheTokenUsage(usage.cacheReadInputTokens(), usage.cacheCreationInputTokens());
        }
    }

    @Singleton
    static final class CacheAwareCostEstimator implements CostEstimator {

        private final CacheTokenUsageResolver resolver;

        CacheAwareCostEstimator(CacheTokenUsageResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public boolean supports(SupportsContext context) {
            return "cache-model".equals(context.model());
        }

        @Override
        public CostResult estimate(CostContext context) {
            TokenUsage tokenUsage = context.responseContext().chatResponse().tokenUsage();
            CacheTokenUsage cache = resolver.resolve(tokenUsage);
            return new CostResult(
                    BigDecimal.valueOf(context.inputTokens()),
                    BigDecimal.valueOf(context.outputTokens()),
                    BigDecimal.valueOf(cache.cacheReadInputTokens()),
                    BigDecimal.valueOf(cache.cacheCreationInputTokens()),
                    "USD");
        }
    }
}
