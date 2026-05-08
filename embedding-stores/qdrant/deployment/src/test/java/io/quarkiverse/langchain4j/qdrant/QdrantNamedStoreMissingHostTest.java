package io.quarkiverse.langchain4j.qdrant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.config.ConfigValidationException;

public class QdrantNamedStoreMissingHostTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.qdrant.devservices.enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.qdrant.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.qdrant.products.collection-name", "product_embeddings");

    @Inject
    @EmbeddingStoreName("products")
    QdrantEmbeddingStore productsEmbeddingStore;

    @Test
    void testMissingHost() {
        assertThatThrownBy(() -> productsEmbeddingStore.toString())
                .hasCauseInstanceOf(ConfigValidationException.class);
    }
}
