package io.quarkiverse.langchain4j.cost;

import io.smallrye.common.annotation.Experimental;

/**
 * Provider-neutral view of the prompt cache token counts reported for a single chat response.
 *
 * @param cacheReadInputTokens input tokens served from the prompt cache (cache hit), or {@code null} if unknown
 * @param cacheCreationInputTokens input tokens written to the prompt cache, or {@code null} if unknown
 */
@Experimental("This feature is experimental and the API is subject to change")
public record CacheTokenUsage(Integer cacheReadInputTokens, Integer cacheCreationInputTokens) {

    public static CacheTokenUsage none() {
        return new CacheTokenUsage(0, 0);
    }
}
