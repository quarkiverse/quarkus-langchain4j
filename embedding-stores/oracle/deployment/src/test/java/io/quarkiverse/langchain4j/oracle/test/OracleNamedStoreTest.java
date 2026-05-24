package io.quarkiverse.langchain4j.oracle.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkiverse.langchain4j.oracle.QuarkusOracleEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class OracleNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.oracle.create-option", "CREATE_OR_REPLACE")
            .overrideConfigKey("quarkus.langchain4j.oracle.products.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.oracle.products.create-option", "CREATE_OR_REPLACE")
            .overrideRuntimeConfigKey("quarkus.langchain4j.oracle.products.table", "product_embeddings")
            .overrideConfigKey("quarkus.class-loading.parent-first-artifacts", "ai.djl.huggingface:tokenizers");

    @Inject
    QuarkusOracleEmbeddingStore defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsEmbeddingStore;

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
        assertThat((Object) defaultEmbeddingStore).isNotSameAs(productsEmbeddingStore);
    }
}
