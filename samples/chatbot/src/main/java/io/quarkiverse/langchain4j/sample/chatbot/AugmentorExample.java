package io.quarkiverse.langchain4j.sample.chatbot;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;

@ApplicationScoped
public class AugmentorExample implements Supplier<RetrievalAugmentor> {

    private final EmbeddingStoreContentRetriever retriever;

    AugmentorExample(EmbeddingStore store, EmbeddingModel model) {
        retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(model)
                .embeddingStore(store)
                .maxResults(20)
                .build();
    }

    @Override
    public RetrievalAugmentor get() {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(retriever)
                .build();
    }
}
