package io.quarkiverse.langchain4j.openai.common.runtime;

import jakarta.inject.Singleton;

import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.AbstractTypeSupportingCacheTokenUsageExtractor;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;

/**
 * Exposes OpenAI prompt cache token counts ({@link OpenAiTokenUsage.InputTokensDetails#cachedTokens()}) to the
 * provider-neutral cost and metrics machinery in core. OpenAI reports only cached (read) input tokens, so the
 * cache-creation slot is always {@code null}.
 */
@Singleton
public class OpenAiCacheTokenUsageExtractor
        extends AbstractTypeSupportingCacheTokenUsageExtractor<OpenAiTokenUsage> {

    @Override
    public CacheTokenUsage extract(TokenUsage tokenUsage) {
        OpenAiTokenUsage openAiTokenUsage = (OpenAiTokenUsage) tokenUsage;
        OpenAiTokenUsage.InputTokensDetails inputTokensDetails = openAiTokenUsage.inputTokensDetails();
        Integer cacheReadInputTokens = inputTokensDetails != null ? inputTokensDetails.cachedTokens() : null;
        return new CacheTokenUsage(cacheReadInputTokens, null);
    }
}
