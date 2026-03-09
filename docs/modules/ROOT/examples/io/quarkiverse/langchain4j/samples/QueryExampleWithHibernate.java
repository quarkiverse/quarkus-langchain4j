package io.quarkiverse.langchain4j.samples;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.hibernate.EmbeddingEntity;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class QueryExampleWithHibernate {

    /**
     * The embedding store (the database).
     * The bean is provided by the quarkus-langchain4j-hibernate extension.
     */
    @Inject
    HibernateEmbeddingStore<EmbeddingEntity> store;

    /**
     * The embedding model (how is computed the vector of a document).
     * The bean is provided by the LLM (like openai) extension.
     */
    @Inject
    EmbeddingModel embeddingModel;

    public void ingest() {
        // User's question
        String question = "What is the refund policy?";

        // Generate embedding for the question
        Embedding questionEmbedding = embeddingModel.embed(question).content();

        // Search for the most similar text segments (top 3 results)
        EmbeddingSearchResult<TextSegment> searchResult = store.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(questionEmbedding)
                        .maxResults(3)  // Retrieve top 3 most similar chunks
                        .build()
        );

        for (EmbeddingMatch<TextSegment> match : searchResult.matches()) {
            System.out.println("Matching id: " + match.embeddingId());
        }
    }
}
