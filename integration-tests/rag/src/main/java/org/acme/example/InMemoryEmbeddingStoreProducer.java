package org.acme.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

public class InMemoryEmbeddingStoreProducer {

    @Inject
    EmbeddingModel embeddingModel;

    @Produces
    @ApplicationScoped
    public InMemoryEmbeddingStore<TextSegment> getStore() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        embed(store, "Charlie was born in 2018.");
        embed(store, "David was born in 2019.");
        return store;
    }

    private void embed(InMemoryEmbeddingStore<TextSegment> store, String text) {
        store.add(embeddingModel.embed(text).content(), new TextSegment(text, new Metadata()));
    }
}
