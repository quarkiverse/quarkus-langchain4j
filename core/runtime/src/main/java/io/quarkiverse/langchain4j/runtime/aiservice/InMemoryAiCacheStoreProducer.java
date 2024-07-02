package io.quarkiverse.langchain4j.runtime.aiservice;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.cache.InMemoryAiCacheStore;
import io.quarkus.arc.DefaultBean;

/**
 * Creates the default {@link InMemoryAiCacheStoreProducer} store to be used by classes annotated with {@link RegisterAiService}
 */
public class InMemoryAiCacheStoreProducer {

    @Produces
    @Singleton
    @DefaultBean
    public InMemoryAiCacheStore chatMemoryStore() {
        return new InMemoryAiCacheStore();
    }
}
