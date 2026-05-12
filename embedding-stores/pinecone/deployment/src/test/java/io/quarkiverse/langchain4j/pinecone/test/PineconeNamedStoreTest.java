package io.quarkiverse.langchain4j.pinecone.test;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

public class PineconeNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.api-key", "test-api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.environment", "gcp-starter")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.project-id", "proj123")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.index-name", "default-index")
            .overrideConfigKey("quarkus.langchain4j.pinecone.products.index-name", "products-index")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.api-key", "test-api-key-2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.environment", "us-west1-gcp")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.project-id", "proj456")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.namespace", "product-ns");

    @Inject
    EmbeddingStore<TextSegment> defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsEmbeddingStore;

    @Test
    void testDefault() {
        Assertions.assertThat(defaultEmbeddingStore).isNotNull();
    }

    @Test
    void testNamed() {
        Assertions.assertThat(productsEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        Assertions.assertThat(defaultEmbeddingStore).isNotSameAs(productsEmbeddingStore);
    }
}
