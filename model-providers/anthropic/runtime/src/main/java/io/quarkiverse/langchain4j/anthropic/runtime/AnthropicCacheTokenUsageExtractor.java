package io.quarkiverse.langchain4j.anthropic.runtime;

import jakarta.inject.Singleton;

import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.cost.AbstractTypeSupportingCacheTokenUsageExtractor;
import io.quarkiverse.langchain4j.cost.CacheTokenUsage;

/**
 * Exposes Anthropic prompt cache token counts ({@link AnthropicTokenUsage#cacheReadInputTokens()} and
 * {@link AnthropicTokenUsage#cacheCreationInputTokens()}) to the provider-neutral cost and metrics machinery in core.
 */
@Singleton
public class AnthropicCacheTokenUsageExtractor
        extends AbstractTypeSupportingCacheTokenUsageExtractor<AnthropicTokenUsage> {

    @Override
    public CacheTokenUsage extract(TokenUsage tokenUsage) {
        AnthropicTokenUsage anthropicTokenUsage = (AnthropicTokenUsage) tokenUsage;
        return new CacheTokenUsage(anthropicTokenUsage.cacheReadInputTokens(),
                anthropicTokenUsage.cacheCreationInputTokens());
    }
}
