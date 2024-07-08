package io.quarkiverse.langchain4j.runtime.cache;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link AiCacheStore}.
 * <p>
 * This storage mechanism is transient and does not persist data across application restarts.
 */
public class InMemoryAiCacheStore implements AiCacheStore {

    private final Map<Object, List<CacheRecord>> store = new ConcurrentHashMap<>();

    @Override
    public List<CacheRecord> getAll(Object memoryId) {
        var elements = store.get(memoryId);
        if (elements == null)
            return new LinkedList<>();
        return elements;
    }

    @Override
    public void deleteCache(Object memoryId) {
        store.remove(memoryId);
    }

    @Override
    public void updateCache(Object memoryId, List<CacheRecord> elements) {
        store.put(memoryId, elements);
    }
}
