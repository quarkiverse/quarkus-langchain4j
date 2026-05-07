package io.quarkiverse.langchain4j.milvus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

public class MilvusNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.milvus.dimension", "384")
            .overrideRuntimeConfigKey("quarkus.langchain4j.milvus.consistency-level", "STRONG")
            .overrideConfigKey("quarkus.langchain4j.milvus.products.host", "<default>")
            .overrideRuntimeConfigKey("quarkus.langchain4j.milvus.products.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.milvus.products.collection-name", "product_embeddings");

    @Inject
    MilvusEmbeddingStore defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    MilvusEmbeddingStore productsEmbeddingStore;

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
