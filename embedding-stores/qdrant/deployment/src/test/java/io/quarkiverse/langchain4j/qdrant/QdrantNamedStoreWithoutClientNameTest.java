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

/**
 * Unlike the other embedding stores, every Qdrant store property is a build-time one, so SmallRye Config materializes a
 * named store key for any of them and no extra discovery is needed. This pins that down: named stores are found without
 * the {@code client-name} property that the other tests set.
 */
public class QdrantNamedStoreWithoutClientNameTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.qdrant.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.qdrant.products.collection-name", "product_embeddings")
            .overrideConfigKey("quarkus.langchain4j.qdrant.documents.payload-text-key", "content");

    @Inject
    @EmbeddingStoreName("products")
    QdrantEmbeddingStore productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    QdrantEmbeddingStore documentsEmbeddingStore;

    @Test
    void testBothNamed() {
        assertThat(productsEmbeddingStore).isNotNull();
        assertThat(documentsEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        assertThat(productsEmbeddingStore).isNotSameAs(documentsEmbeddingStore);
    }
}
