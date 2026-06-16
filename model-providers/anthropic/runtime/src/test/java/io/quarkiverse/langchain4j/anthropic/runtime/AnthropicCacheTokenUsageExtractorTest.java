package io.quarkiverse.langchain4j.anthropic.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;

class AnthropicCacheTokenUsageExtractorTest {

    private final AnthropicCacheTokenUsageExtractor extractor = new AnthropicCacheTokenUsageExtractor();

    @Test
    void mapsAnthropicCacheTokensAndIgnoresPlainTokenUsage() {
        assertThat(extractor.supports(new TokenUsage(10, 20))).isFalse();

        AnthropicTokenUsage usage = AnthropicTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheReadInputTokens(5)
                .cacheCreationInputTokens(3)
                .build();

        assertThat(extractor.supports(usage)).isTrue();

        CacheTokenUsage result = extractor.extract(usage);
        assertThat(result.cacheReadInputTokens()).isEqualTo(5);
        assertThat(result.cacheCreationInputTokens()).isEqualTo(3);
    }
}
