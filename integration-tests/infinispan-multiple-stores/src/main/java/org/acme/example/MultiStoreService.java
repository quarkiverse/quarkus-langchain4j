package org.acme.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;

/**
 * Service demonstrating the use of multiple named Infinispan embedding stores.
 */
@ApplicationScoped
public class MultiStoreService {

    @Inject
    @EmbeddingStoreName("store1")
    EmbeddingStore<TextSegment> store1;

    @Inject
    @EmbeddingStoreName("store2")
    EmbeddingStore<TextSegment> store2;

    @Inject
    EmbeddingStore<TextSegment> defaultStore;

    public EmbeddingStore<TextSegment> getStore1() {
        return store1;
    }

    public EmbeddingStore<TextSegment> getStore2() {
        return store2;
    }

    public EmbeddingStore<TextSegment> getDefaultStore() {
        return defaultStore;
    }
}