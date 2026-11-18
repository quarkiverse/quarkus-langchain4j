package io.quarkiverse.langchain4j.pinecone.test;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.quarkus.test.QuarkusExtensionTest;

public class PineconeMetadataFilterRejectedTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.api-key", "test-api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.environment", "gcp-starter")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.project-id", "proj123")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.index-name", "default-index");

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Test
    void searchWithFilterIsRejectedInsteadOfIgnored() {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(new Embedding(new float[] { 0.1f, 0.2f, 0.3f }))
                .filter(MetadataFilterBuilder.metadataKey("tenant_id").isEqualTo("1"))
                .build();

        try {
            embeddingStore.search(request);
            Assertions.fail("A filtered search must be rejected, not silently answered without the filter");
        } catch (UnsupportedOperationException expected) {
            Assertions.assertThat(expected).hasMessageContaining("does not support metadata filtering");
        }
    }
}
