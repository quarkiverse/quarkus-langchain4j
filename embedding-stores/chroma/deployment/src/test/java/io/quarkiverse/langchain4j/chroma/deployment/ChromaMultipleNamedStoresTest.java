package io.quarkiverse.langchain4j.chroma.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

public class ChromaMultipleNamedStoresTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.chroma.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.chroma.products.collection-name", "product_embeddings")
            .overrideConfigKey("quarkus.langchain4j.chroma.documents.collection-name", "doc_embeddings")
            .overrideRuntimeConfigKey("quarkus.langchain4j.chroma.products.url", "http://localhost:8000")
            .overrideRuntimeConfigKey("quarkus.langchain4j.chroma.documents.url", "http://localhost:8000");

    @Inject
    @EmbeddingStoreName("products")
    ChromaEmbeddingStore productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    ChromaEmbeddingStore documentsEmbeddingStore;

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
