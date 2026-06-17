package io.quarkiverse.langchain4j.ai.runtime.gemini;

import jakarta.inject.Singleton;

import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.AbstractTypeSupportingCacheTokenUsageExtractor;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;

/**
 * Exposes Google AI Gemini prompt cache token counts ({@link GoogleAiGeminiTokenUsage#cachedContentTokenCount()}) to
 * the provider-neutral cost and metrics machinery in core. Gemini reports only cached (read) content tokens, so the
 * cache-creation slot is always {@code null}.
 */
@Singleton
public class AiGeminiCacheTokenUsageExtractor
        extends AbstractTypeSupportingCacheTokenUsageExtractor<GoogleAiGeminiTokenUsage> {

    @Override
    public CacheTokenUsage extract(TokenUsage tokenUsage) {
        GoogleAiGeminiTokenUsage geminiTokenUsage = (GoogleAiGeminiTokenUsage) tokenUsage;
        return new CacheTokenUsage(geminiTokenUsage.cachedContentTokenCount(), null);
    }
}
