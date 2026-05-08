package io.quarkiverse.langchain4j.qdrant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

public class QdrantMultipleNamedStoresTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.qdrant.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.qdrant.products.collection-name", "product_embeddings")
            .overrideConfigKey("quarkus.langchain4j.qdrant.documents.collection-name", "doc_embeddings")
            .overrideRuntimeConfigKey("quarkus.langchain4j.qdrant.products.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.langchain4j.qdrant.documents.host", "localhost");

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
