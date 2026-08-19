package io.quarkiverse.langchain4j.redis.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Named stores must be discovered from any configured property, not only from the build-time {@code client-name} one.
 */
public class RedisNamedStoreWithoutClientNameTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.redis.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.redis.products.dimension", "1536")
            .overrideConfigKey("quarkus.langchain4j.redis.products.index-name", "product_embeddings")
            .overrideConfigKey("quarkus.langchain4j.redis.documents.dimension", "768")
            .overrideConfigKey("quarkus.langchain4j.redis.documents.index-name", "doc_embeddings");

    @Inject
    @EmbeddingStoreName("products")
    RedisEmbeddingStore productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    RedisEmbeddingStore documentsEmbeddingStore;

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
