package io.quarkiverse.langchain4j.lancedb.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.lancedb.LanceDbEmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

public class LanceDbDefaultStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.api-key", "test-api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.database", "test-db")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.dimension", "384");

    @Inject
    LanceDbEmbeddingStore store;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Test
    void testDefaultStoreInjected() {
        assertThat(store).isNotNull();
    }

    @Test
    void testStoreAvailableViaEmbeddingStoreInterface() {
        assertThat(embeddingStore).isNotNull();
        assertThat(embeddingStore).isSameAs(store);
    }
}
