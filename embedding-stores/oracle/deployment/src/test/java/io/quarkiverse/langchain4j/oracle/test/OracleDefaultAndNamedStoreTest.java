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
import io.quarkus.test.QuarkusUnitTest;

public class OracleDefaultAndNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.oracle.create-option", "CREATE_OR_REPLACE")
            .overrideConfigKey("quarkus.langchain4j.oracle.named-store.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.oracle.named-store.create-option", "CREATE_OR_REPLACE")
            .overrideRuntimeConfigKey("quarkus.langchain4j.oracle.named-store.table", "named_embeddings")
            .overrideConfigKey("quarkus.class-loading.parent-first-artifacts", "ai.djl.huggingface:tokenizers");

    @Inject
    EmbeddingStore<TextSegment> defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("named-store")
    EmbeddingStore<TextSegment> namedEmbeddingStore;

    @Test
    void testDefault() {
        assertThat(defaultEmbeddingStore).isNotNull();
    }

    @Test
    void testNamed() {
        assertThat(namedEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        assertThat(defaultEmbeddingStore).isNotSameAs(namedEmbeddingStore);
    }
}
