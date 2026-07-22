package io.quarkiverse.langchain4j.qdrant;

import static org.assertj.core.api.Assertions.assertThat;

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

public class QdrantCreateCollectionsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.qdrant.devservices.service-name", "test_default")
            .overrideConfigKey("quarkus.langchain4j.qdrant.devservices.collection.vector-params.distance", "Cosine")
            .overrideConfigKey("quarkus.langchain4j.qdrant.devservices.collection.vector-params.size", "384")
            .overrideConfigKey("quarkus.langchain4j.qdrant.devservices.create-collections", "true")
            .overrideConfigKey("quarkus.langchain4j.qdrant.products.collection-name", "product_embeddings")
            .overrideConfigKey("quarkus.langchain4j.qdrant.documents.collection-name", "documents");

    @Inject
    QdrantEmbeddingStore defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    QdrantEmbeddingStore productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    QdrantEmbeddingStore documentsEmbeddingStore;

    @Test
    void defaultStoreIsFunctional() {
        var id = defaultEmbeddingStore.add(createEmbedding(), TextSegment.from("test default"));
        assertThat(id).isNotNull();
    }

    @Test
    void namedStoreWithExplicitCollectionNameIsFunctional() {
        var id = productsEmbeddingStore.add(createEmbedding(), TextSegment.from("test products"));
        assertThat(id).isNotNull();
    }

    @Test
    void namedStoreWithStoreNameAsCollectionNameIsFunctional() {
        var id = documentsEmbeddingStore.add(createEmbedding(), TextSegment.from("test documents"));
        assertThat(id).isNotNull();
    }

    @Test
    void storesAreDistinct() {
        assertThat(defaultEmbeddingStore)
                .isNotSameAs(productsEmbeddingStore)
                .isNotSameAs(documentsEmbeddingStore);

        assertThat(productsEmbeddingStore)
                .isNotSameAs(documentsEmbeddingStore);
    }

    private static Embedding createEmbedding() {
        var vector = new float[384];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = i * 0.001f;
        }
        return new Embedding(vector);
    }
}
