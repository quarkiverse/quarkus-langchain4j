package io.quarkiverse.langchain4j.qdrant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that when {@code create-collections} is not enabled (default),
 * named store collections are NOT auto-created, so adding embeddings to them fails.
 */
public class QdrantCreateCollectionsDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.qdrant.devservices.service-name", "test_default")
            .overrideConfigKey("quarkus.langchain4j.qdrant.devservices.collection.vector-params.distance", "Cosine")
            .overrideConfigKey("quarkus.langchain4j.qdrant.devservices.collection.vector-params.size", "384")
            .overrideConfigKey("quarkus.langchain4j.qdrant.products.collection-name", "product_embeddings");

    @Inject
    QdrantEmbeddingStore defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    QdrantEmbeddingStore productsEmbeddingStore;

    @Test
    void defaultStoreWorksBecauseCollectionIsCreated() {
        var id = defaultEmbeddingStore.add(createEmbedding(), TextSegment.from("test default"));
        assertThat(id).isNotNull();
    }

    @Test
    void namedStoreFailsBecauseCollectionWasNotCreated() {
        assertThatThrownBy(() -> productsEmbeddingStore.add(createEmbedding(), TextSegment.from("test products")))
                .isInstanceOf(RuntimeException.class);
    }

    private static Embedding createEmbedding() {
        var vector = new float[384];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = i * 0.001f;
        }
        return new Embedding(vector);
    }
}
