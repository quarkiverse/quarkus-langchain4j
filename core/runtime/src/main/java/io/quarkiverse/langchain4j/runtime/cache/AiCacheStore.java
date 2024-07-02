package io.quarkiverse.langchain4j.runtime.cache;

import java.time.Instant;
import java.util.List;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;

/**
 * Represents a store for the {@link AiCache} state.
 * Allows for flexibility in terms of where and how cache is stored.
 */
public interface AiCacheStore {

    public record CacheRecord(Embedding embedded, AiMessage response, Instant creation) {
        public static CacheRecord of(Embedding embedded, AiMessage response) {
            return new CacheRecord(embedded, response, Instant.now());
        }
    };

    /**
     * Get all items stored in the cache.
     *
     * @param id Unique identifier for the cache
     * @return {@link List} of {@link CacheRecord}
     */
    public List<CacheRecord> getAll(Object id);

    /**
     * Delete all items stored in the cache.
     *
     * @param id Unique identifier for the cache
     */
    public void deleteCache(Object id);

    /**
     * Update all items stored in the cache.
     *
     * @param id Unique identifier for the cache
     * @param items Items to update
     */
    public void updateCache(Object id, List<CacheRecord> items);
}
