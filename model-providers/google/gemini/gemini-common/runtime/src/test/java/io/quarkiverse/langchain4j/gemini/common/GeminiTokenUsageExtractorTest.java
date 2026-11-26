package io.quarkiverse.langchain4j.gemini.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;

class GeminiTokenUsageExtractorTest {

    private final GeminiTokenUsageExtractor extractor = new GeminiTokenUsageExtractor();

    @Test
    void mapsCachedContentAndThoughtsAndIgnoresPlainTokenUsage() {
        assertFalse(extractor.supports(new TokenUsage(10, 20)));

        GeminiTokenUsage usage = new GeminiTokenUsage(100, 50, 190, 7, 40);

        assertTrue(extractor.supports(usage));

        CacheTokenUsage cacheTokenUsage = extractor.extract(usage);
        assertEquals(7, cacheTokenUsage.cacheReadInputTokens());
        assertNull(cacheTokenUsage.cacheCreationInputTokens());

        assertEquals(40, extractor.reasoningOutputTokens(usage));
        assertFalse(extractor.outputTokenCountIncludesReasoning());
    }

    @Test
    void returnsNullWhenGeminiReportsNoCacheOrThoughts() {
        GeminiTokenUsage usage = new GeminiTokenUsage(100, 50, 150, null, null);

        assertNull(extractor.extract(usage).cacheReadInputTokens());
        assertNull(extractor.reasoningOutputTokens(usage));
    }
}
