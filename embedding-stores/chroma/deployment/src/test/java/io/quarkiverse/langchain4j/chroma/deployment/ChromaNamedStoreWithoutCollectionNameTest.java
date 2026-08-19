package io.quarkiverse.langchain4j.chroma.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Named stores must be discovered from any configured property, not only from the build-time {@code collection-name} one.
 */
public class ChromaNamedStoreWithoutCollectionNameTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.chroma.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.chroma.devservices.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.chroma.products.url", "http://localhost:8000")
            .overrideConfigKey("quarkus.langchain4j.chroma.documents.url", "http://localhost:8000");

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

    /**
     * {@code devservices} is a root property rather than a store name, so it must not produce a store.
     */
    @Test
    void testDevservicesIsNotAStore() {
        assertThat(Arc.container()
                .instance(ChromaEmbeddingStore.class, EmbeddingStoreName.Literal.of("devservices"))
                .isAvailable()).isFalse();
    }
}
