package io.quarkiverse.langchain4j.weaviate.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;
import io.weaviate.client.WeaviateClient;

public class WeaviateMultipleNamedStoresTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.weaviate.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.weaviate.products.object-class", "ProductClass")
            .overrideConfigKey("quarkus.langchain4j.weaviate.documents.object-class", "DocumentClass")
            .overrideRuntimeConfigKey("quarkus.langchain4j.weaviate.products.metadata.keys", "tags")
            .overrideRuntimeConfigKey("quarkus.langchain4j.weaviate.documents.metadata.keys", "tags");

    @Inject
    @EmbeddingStoreName("products")
    WeaviateEmbeddingStore productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    WeaviateClient productsClient;

    @Inject
    @EmbeddingStoreName("documents")
    WeaviateEmbeddingStore documentsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    WeaviateClient documentsClient;

    @Test
    void testBothNamed() {
        assertThat(productsEmbeddingStore).isNotNull();
        assertThat(productsClient).isNotNull();
        assertThat(documentsEmbeddingStore).isNotNull();
        assertThat(documentsClient).isNotNull();
    }

    @Test
    void testNotSame() {
        assertThat(productsEmbeddingStore).isNotSameAs(documentsEmbeddingStore);
        assertThat(productsClient).isNotSameAs(documentsClient);
    }
}
