package io.quarkiverse.langchain4j.gemini.common;

import jakarta.inject.Singleton;

import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.AbstractTypeSupportingCacheTokenUsageExtractor;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;
import io.quarkiverse.langchain4j.cost.ReasoningTokenUsageExtractor;

/**
 * Exposes the cached and thinking token counts of {@link GeminiTokenUsage}, which the Google AI Gemini and Vertex AI
 * Gemini chat models report, to the provider-neutral metrics and tracing machinery in core.
 * <p>
 * Gemini reports only cached (read) content tokens, so the cache-creation slot is always {@code null}. Its
 * {@code candidatesTokenCount}, which becomes {@link TokenUsage#outputTokenCount()}, excludes the thinking tokens.
 */
@Singleton
public class GeminiTokenUsageExtractor extends AbstractTypeSupportingCacheTokenUsageExtractor<GeminiTokenUsage>
        implements ReasoningTokenUsageExtractor {

    @Override
    public CacheTokenUsage extract(TokenUsage tokenUsage) {
        return new CacheTokenUsage(((GeminiTokenUsage) tokenUsage).cachedContentTokenCount(), null);
    }

    @Override
    public Integer reasoningOutputTokens(TokenUsage tokenUsage) {
        return ((GeminiTokenUsage) tokenUsage).thoughtsTokenCount();
    }

    @Override
    public boolean outputTokenCountIncludesReasoning() {
        return false;
    }
}
