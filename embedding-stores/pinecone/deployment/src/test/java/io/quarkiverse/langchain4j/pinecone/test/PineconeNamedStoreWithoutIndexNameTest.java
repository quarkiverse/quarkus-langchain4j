package io.quarkiverse.langchain4j.pinecone.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Named stores must be discovered from any configured property, not only from the build-time {@code index-name} one.
 */
public class PineconeNamedStoreWithoutIndexNameTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.pinecone.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.pinecone.products.api-key", "test-api-key-products")
            .overrideConfigKey("quarkus.langchain4j.pinecone.products.environment", "gcp-starter")
            .overrideConfigKey("quarkus.langchain4j.pinecone.products.project-id", "proj123")
            .overrideConfigKey("quarkus.langchain4j.pinecone.products.dimension", "1536")
            .overrideConfigKey("quarkus.langchain4j.pinecone.documents.api-key", "test-api-key-docs")
            .overrideConfigKey("quarkus.langchain4j.pinecone.documents.environment", "us-west1-gcp")
            .overrideConfigKey("quarkus.langchain4j.pinecone.documents.project-id", "proj456")
            .overrideConfigKey("quarkus.langchain4j.pinecone.documents.dimension", "768");

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    EmbeddingStore<TextSegment> documentsEmbeddingStore;

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
