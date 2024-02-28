package io.quarkiverse.langchain4j.samples;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.retriever.Retriever;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;

@ApplicationScoped
public class DocumentRetrieverExample implements Retriever<TextSegment> {

    private final EmbeddingStoreRetriever retriever;

    DocumentRetrieverExample(RedisEmbeddingStore store, EmbeddingModel model) {
        retriever = EmbeddingStoreRetriever.from(store, model, 20);
    }

    @Override
    public List<TextSegment> findRelevant(String s) {
        return retriever.findRelevant(s);
    }
}
