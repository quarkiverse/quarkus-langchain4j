package org.acme.example;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.runtime.StartupEvent;

public class EmbeddingStoreIngestor {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @SuppressWarnings("unused")
    public void ingest(@Observes StartupEvent event) {
        embed(embeddingStore, "Charlie was born in 2018.", Metadata.metadata("userId", "1"));
        embed(embeddingStore, "Charlie was born in 2015.", Metadata.metadata("userId", "2"));
        embed(embeddingStore, "David was born in 2019.", Metadata.metadata("userId", "1"));

    }

    private void embed(EmbeddingStore<TextSegment> store, String text, Metadata metadata) {
        store.add(embeddingModel.embed(text).content(), new TextSegment(text, metadata));
    }
}
