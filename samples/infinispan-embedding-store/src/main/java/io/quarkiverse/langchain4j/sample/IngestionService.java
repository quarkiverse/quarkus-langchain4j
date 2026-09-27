package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Stores text segments as vectors in Infinispan and runs semantic search over them.
 */
@ApplicationScoped
public class IngestionService {

    @Inject
    EmbeddingStore store;

    @Inject
    EmbeddingModel model;

    public void ingest(List<TextSegment> segments) {
        store.addAll(segments);
    }

    public EmbeddingSearchResult<TextSegment> search(String query, int maxResults) {
        Embedding queryEmbedding = model.embed(query).content();
        return store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(maxResults)
                .minScore(0.0)
                .build());
    }
}
