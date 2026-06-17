package io.quarkiverse.langchain4j.bedrock.runtime;

import jakarta.inject.Singleton;

import dev.langchain4j.model.bedrock.BedrockTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.AbstractTypeSupportingCacheTokenUsageExtractor;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;

/**
 * Exposes Amazon Bedrock prompt cache token counts ({@link BedrockTokenUsage#cacheReadInputTokens()} and
 * {@link BedrockTokenUsage#cacheWriteInputTokens()}) to the provider-neutral cost and metrics machinery in core.
 */
@Singleton
public class BedrockCacheTokenUsageExtractor extends AbstractTypeSupportingCacheTokenUsageExtractor<BedrockTokenUsage> {

    @Override
    public CacheTokenUsage extract(TokenUsage tokenUsage) {
        BedrockTokenUsage bedrockTokenUsage = (BedrockTokenUsage) tokenUsage;
        return new CacheTokenUsage(bedrockTokenUsage.cacheReadInputTokens(), bedrockTokenUsage.cacheWriteInputTokens());
    }
}
