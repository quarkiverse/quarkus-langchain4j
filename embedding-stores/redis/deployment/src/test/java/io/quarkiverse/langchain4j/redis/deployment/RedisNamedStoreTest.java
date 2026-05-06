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

public class RedisNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.redis.dimension", "384")
            .overrideConfigKey("quarkus.langchain4j.redis.products.client-name", "<default>")
            .overrideRuntimeConfigKey("quarkus.langchain4j.redis.products.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.redis.products.index-name", "product_embeddings");

    @Inject
    RedisEmbeddingStore defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    RedisEmbeddingStore productsEmbeddingStore;

    @Test
    void testDefault() {
        assertThat(defaultEmbeddingStore).isNotNull();
    }

    @Test
    void testNamed() {
        assertThat(productsEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        assertThat(defaultEmbeddingStore).isNotSameAs(productsEmbeddingStore);
    }
}
