package io.quarkiverse.langchain4j.qdrant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.qdrant.runtime.QdrantEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class QdrantNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.qdrant.products.collection-name", "product_embeddings")
            .overrideConfigKey("quarkus.langchain4j.qdrant.products.client-name", "secondary")
            .overrideConfigKey("quarkus.qdrant.\"secondary\".host", "localhost");

    @Inject
    QdrantEmbeddingStore defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    QdrantEmbeddingStore productsEmbeddingStore;

    @Test
    void testDefault() {
        assertThat(defaultEmbeddingStore).isNotNull();
    }

    @Test
    void testNamed() {
        assertThat(productsEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        assertThat(defaultEmbeddingStore).isNotSameAs(productsEmbeddingStore);
    }
}
