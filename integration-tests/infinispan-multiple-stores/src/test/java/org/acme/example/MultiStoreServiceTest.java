package org.acme.example;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for multiple named Infinispan embedding stores.
 * This test verifies that:
 * 1. Multiple named stores can be injected and used independently
 * 2. Each store maintains its own separate data
 * 3. The default store works alongside named stores
 */
@QuarkusTest
public class MultiStoreServiceTest {

    @Inject
    MultiStoreService multiStoreService;

    @Test
    public void testMultipleNamedStores() {
        // Verify all stores are injected
        assertThat(multiStoreService.getStore1()).isNotNull();
        assertThat(multiStoreService.getStore2()).isNotNull();
        assertThat(multiStoreService.getDefaultStore()).isNotNull();

        // Create test embeddings with different dimensions
        float[] embedding1Values = new float[384];
        float[] embedding2Values = new float[384];
        float[] embedding3Values = new float[384];

        for (int i = 0; i < 384; i++) {
            embedding1Values[i] = 0.1f + (i * 0.001f);
            embedding2Values[i] = 0.2f + (i * 0.001f);
            embedding3Values[i] = 0.3f + (i * 0.001f);
        }

        Embedding embedding1 = new Embedding(embedding1Values);
        Embedding embedding2 = new Embedding(embedding2Values);
        Embedding embedding3 = new Embedding(embedding3Values);

        TextSegment segment1 = TextSegment.from("Document for store 1");
        TextSegment segment2 = TextSegment.from("Document for store 2");
        TextSegment segment3 = TextSegment.from("Document for default store");

        // Add embeddings to different stores
        String id1 = multiStoreService.getStore1().add(embedding1, segment1);
        String id2 = multiStoreService.getStore2().add(embedding2, segment2);
        String id3 = multiStoreService.getDefaultStore().add(embedding3, segment3);

        assertThat(id1).isNotNull();
        assertThat(id2).isNotNull();
        assertThat(id3).isNotNull();

        // Verify each store contains only its own data
        EmbeddingSearchResult<TextSegment> result1 = multiStoreService.getStore1()
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding1)
                        .maxResults(10)
                        .build());

        assertThat(result1.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match1 = result1.matches().get(0);
        assertThat(match1.embedded().text()).isEqualTo("Document for store 1");

        EmbeddingSearchResult<TextSegment> result2 = multiStoreService.getStore2()
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding2)
                        .maxResults(10)
                        .build());

        assertThat(result2.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match2 = result2.matches().get(0);
        assertThat(match2.embedded().text()).isEqualTo("Document for store 2");

        EmbeddingSearchResult<TextSegment> result3 = multiStoreService.getDefaultStore()
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding3)
                        .maxResults(10)
                        .build());

        assertThat(result3.matches()).hasSize(1);
        EmbeddingMatch<TextSegment> match3 = result3.matches().get(0);
        assertThat(match3.embedded().text()).isEqualTo("Document for default store");

        // Verify stores are isolated - searching store1 with embedding2 should not find store2's data
        EmbeddingSearchResult<TextSegment> crossResult = multiStoreService.getStore1()
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embedding2)
                        .maxResults(10)
                        .minScore(0.9) // High threshold to ensure we only get exact matches
                        .build());

        // Should not find store2's document in store1
        assertThat(crossResult.matches()).isEmpty();
    }
}