package io.quarkiverse.langchain4j.milvus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Named stores must be discovered from any configured property, not only from the build-time {@code host} one.
 */
public class MilvusNamedStoreWithoutHostTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.milvus.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.milvus.devservices.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.milvus.products.dimension", "1536")
            .overrideConfigKey("quarkus.langchain4j.milvus.products.collection-name", "product_embeddings")
            .overrideConfigKey("quarkus.langchain4j.milvus.documents.dimension", "768")
            .overrideConfigKey("quarkus.langchain4j.milvus.documents.collection-name", "doc_embeddings");

    @Inject
    @EmbeddingStoreName("products")
    MilvusEmbeddingStore productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    MilvusEmbeddingStore documentsEmbeddingStore;

    @Test
    void testBothNamed() {
        assertThat(productsEmbeddingStore).isNotNull();
        assertThat(documentsEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        assertThat(productsEmbeddingStore).isNotSameAs(documentsEmbeddingStore);
    }

    /**
     * {@code devservices} is a root property rather than a store name, so it must not produce a store.
     */
    @Test
    void testDevservicesIsNotAStore() {
        assertThat(Arc.container()
                .instance(MilvusEmbeddingStore.class, EmbeddingStoreName.Literal.of("devservices"))
                .isAvailable()).isFalse();
    }
}
