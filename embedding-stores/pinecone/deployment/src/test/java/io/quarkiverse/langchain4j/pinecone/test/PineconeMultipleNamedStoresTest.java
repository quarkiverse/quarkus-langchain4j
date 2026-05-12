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

public class PineconeMultipleNamedStoresTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.pinecone.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.pinecone.products.index-name", "products-index")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.api-key", "test-api-key-products")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.environment", "gcp-starter")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.project-id", "proj123")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.products.dimension", "1536")
            .overrideConfigKey("quarkus.langchain4j.pinecone.documents.index-name", "docs-index")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.documents.api-key", "test-api-key-docs")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.documents.environment", "us-west1-gcp")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.documents.project-id", "proj456")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pinecone.documents.dimension", "768");

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    EmbeddingStore<TextSegment> documentsEmbeddingStore;

    @Test
    void testBothNamed() {
        Assertions.assertThat(productsEmbeddingStore).isNotNull();
        Assertions.assertThat(documentsEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        Assertions.assertThat(productsEmbeddingStore).isNotSameAs(documentsEmbeddingStore);
    }
}
