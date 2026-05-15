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

public class WeaviateNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.weaviate.products.object-class", "ProductClass")
            .overrideRuntimeConfigKey("quarkus.langchain4j.weaviate.products.metadata.keys", "tags");

    @Inject
    WeaviateEmbeddingStore defaultEmbeddingStore;

    @Inject
    WeaviateClient defaultClient;

    @Inject
    @EmbeddingStoreName("products")
    WeaviateEmbeddingStore productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    WeaviateClient productsClient;

    @Test
    void testDefault() {
        assertThat(defaultEmbeddingStore).isNotNull();
        assertThat(defaultClient).isNotNull();
    }

    @Test
    void testNamed() {
        assertThat(productsEmbeddingStore).isNotNull();
        assertThat(productsClient).isNotNull();
    }

    @Test
    void testNotSame() {
        assertThat(defaultEmbeddingStore).isNotSameAs(productsEmbeddingStore);
        assertThat(defaultClient).isNotSameAs(productsClient);
    }
}
