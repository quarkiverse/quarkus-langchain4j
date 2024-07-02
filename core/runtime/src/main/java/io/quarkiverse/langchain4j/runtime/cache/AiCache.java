package io.quarkiverse.langchain4j.runtime.cache;

import java.util.Optional;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;

/**
 * Represents the cache of a AI. It can be used to reduces response time for similar queries.
 */
public interface AiCache {

    /**
     * The ID of the {@link AiCache}.
     *
     * @return The ID of the {@link AiCache}.
     */
    Object id();

    /**
     * Cache a new message.
     *
     * @param query Embedded value to add to the cache.
     * @param response Response returned by the AI to add to the cache.
     */
    void add(Embedding query, AiMessage response);

    /**
     * Check to see if there is a response in the cache that is semantically close to a cached query.
     *
     * @param query
     * @return
     */
    Optional<AiMessage> search(Embedding query);

    /**
     * Clears the cache.
     */
    void clear();
}
