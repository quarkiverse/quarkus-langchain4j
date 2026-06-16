package io.quarkiverse.langchain4j.cost;

import dev.langchain4j.model.output.TokenUsage;
import io.smallrye.common.annotation.Experimental;

/**
 * SPI that exposes a provider's prompt cache token counts in a provider-neutral way. Implement it in the provider
 * module (where the provider {@link TokenUsage} subtype is available) and expose it as a CDI bean; the core consults
 * all available extractors, so it never depends on a specific provider.
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface CacheTokenUsageExtractor {

    /**
     * Whether this extractor is able to read cache token counts from the given usage.
     */
    boolean supports(TokenUsage tokenUsage);

    /**
     * Extracts the cache token counts from the given usage. Only called when {@link #supports(TokenUsage)} returns
     * {@code true}.
     */
    CacheTokenUsage extract(TokenUsage tokenUsage);
}
