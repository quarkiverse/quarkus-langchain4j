package io.quarkiverse.langchain4j.bedrock.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.bedrock.BedrockTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;

class BedrockCacheTokenUsageExtractorTest {

    private final BedrockCacheTokenUsageExtractor extractor = new BedrockCacheTokenUsageExtractor();

    @Test
    void mapsBedrockCacheTokensAndIgnoresPlainTokenUsage() {
        assertThat(extractor.supports(new TokenUsage(10, 20))).isFalse();

        BedrockTokenUsage usage = BedrockTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .cacheReadInputTokens(5)
                .cacheWriteInputTokens(3)
                .build();

        assertThat(extractor.supports(usage)).isTrue();

        CacheTokenUsage result = extractor.extract(usage);
        assertThat(result.cacheReadInputTokens()).isEqualTo(5);
        assertThat(result.cacheCreationInputTokens()).isEqualTo(3);
    }
}
