package io.quarkiverse.langchain4j.weaviate.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Named stores must be discovered from any configured property, not only from the build-time {@code object-class} one.
 */
public class WeaviateNamedStoreWithoutObjectClassTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.weaviate.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.weaviate.devservices.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.weaviate.products.host", "localhost")
            .overrideConfigKey("quarkus.langchain4j.weaviate.products.metadata.keys", "tags")
            .overrideConfigKey("quarkus.langchain4j.weaviate.documents.host", "localhost")
            .overrideConfigKey("quarkus.langchain4j.weaviate.documents.metadata.keys", "tags");

    @Inject
    @EmbeddingStoreName("products")
    WeaviateEmbeddingStore productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    WeaviateEmbeddingStore documentsEmbeddingStore;

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
                .instance(WeaviateEmbeddingStore.class, EmbeddingStoreName.Literal.of("devservices"))
                .isAvailable()).isFalse();
    }
}
