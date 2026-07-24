package io.quarkiverse.langchain4j.lancedb.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.lancedb.LanceDbEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class LanceDbNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.api-key", "test-api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.database", "test-db")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.dimension", "384")
            .overrideConfigKey("quarkus.langchain4j.lancedb.products.database", "products-db")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.products.api-key", "test-api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.products.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.products.table-name", "product_embeddings");

    @Inject
    LanceDbEmbeddingStore defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    LanceDbEmbeddingStore productsEmbeddingStore;

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
