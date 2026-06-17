package io.quarkiverse.langchain4j.openai.common.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;

class OpenAiCacheTokenUsageExtractorTest {

    private final OpenAiCacheTokenUsageExtractor extractor = new OpenAiCacheTokenUsageExtractor();

    @Test
    void mapsCachedTokensAndIgnoresPlainTokenUsage() {
        assertFalse(extractor.supports(new TokenUsage(10, 20)));

        OpenAiTokenUsage usage = OpenAiTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder().cachedTokens(8).build())
                .build();

        assertTrue(extractor.supports(usage));

        CacheTokenUsage result = extractor.extract(usage);
        assertEquals(Integer.valueOf(8), result.cacheReadInputTokens());
        assertNull(result.cacheCreationInputTokens());
    }

    @Test
    void handlesMissingInputTokensDetails() {
        OpenAiTokenUsage usage = OpenAiTokenUsage.builder()
                .inputTokenCount(100)
                .outputTokenCount(50)
                .build();

        CacheTokenUsage result = extractor.extract(usage);
        assertNull(result.cacheReadInputTokens());
        assertNull(result.cacheCreationInputTokens());
    }
}
