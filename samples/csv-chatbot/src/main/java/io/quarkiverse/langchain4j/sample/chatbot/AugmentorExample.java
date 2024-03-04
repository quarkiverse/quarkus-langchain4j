package io.quarkiverse.langchain4j.sample.chatbot;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;

@ApplicationScoped
public class AugmentorExample implements Supplier<RetrievalAugmentor> {

    private final EmbeddingStoreContentRetriever retriever;

    AugmentorExample(RedisEmbeddingStore store, EmbeddingModel model) {
        retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(model)
                .maxResults(10)
                .build();
    }

    @Override
    public RetrievalAugmentor get() {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(retriever)
                .build();
    }
}
