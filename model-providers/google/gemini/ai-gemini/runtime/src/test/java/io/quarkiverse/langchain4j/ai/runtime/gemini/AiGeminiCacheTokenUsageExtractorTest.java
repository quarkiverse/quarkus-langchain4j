package io.quarkiverse.langchain4j.ai.runtime.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;

class AiGeminiCacheTokenUsageExtractorTest {

    private final AiGeminiCacheTokenUsageExtractor extractor = new AiGeminiCacheTokenUsageExtractor();

    @Test
    void mapsGeminiCachedContentAndIgnoresPlainTokenUsage() {
        assertThat(extractor.supports(new TokenUsage(10, 20))).isFalse();

        GoogleAiGeminiTokenUsage usage = GoogleAiGeminiTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cachedContentTokenCount(7)
                .build();

        assertThat(extractor.supports(usage)).isTrue();

        CacheTokenUsage result = extractor.extract(usage);
        assertThat(result.cacheReadInputTokens()).isEqualTo(7);
        assertThat(result.cacheCreationInputTokens()).isNull();
    }
}
