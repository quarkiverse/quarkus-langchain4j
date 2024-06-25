package io.quarkiverse.langchain4j.runtime.cache;

import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Provides instances of {@link AiCache}.
 * Intended to be used with {@link RegisterAiService}
 */
@FunctionalInterface
public interface AiCacheProvider {

    /**
     * Provides an instance of {@link AiCache}.
     *
     * @param id The ID of the cache.
     * @return A {@link AiCache} instance.
     */
    AiCache get(Object id);
}
