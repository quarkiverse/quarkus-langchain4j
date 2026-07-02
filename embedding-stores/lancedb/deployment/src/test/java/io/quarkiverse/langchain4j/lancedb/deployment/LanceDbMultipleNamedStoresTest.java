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

public class LanceDbMultipleNamedStoresTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.lancedb.default-store-enabled", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.products.api-key", "test-api-key")
            .overrideConfigKey("quarkus.langchain4j.lancedb.products.database", "products-db")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.products.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.products.table-name", "product_embeddings")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.documents.api-key", "test-api-key")
            .overrideConfigKey("quarkus.langchain4j.lancedb.documents.database", "documents-db")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.documents.dimension", "768")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.documents.table-name", "document_embeddings");

    @Inject
    @EmbeddingStoreName("products")
    LanceDbEmbeddingStore productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    LanceDbEmbeddingStore documentsEmbeddingStore;

    @Test
    void testProductsStore() {
        assertThat(productsEmbeddingStore).isNotNull();
    }

    @Test
    void testDocumentsStore() {
        assertThat(documentsEmbeddingStore).isNotNull();
    }

    @Test
    void testStoresAreDifferent() {
        assertThat(productsEmbeddingStore).isNotSameAs(documentsEmbeddingStore);
    }
}
