package io.quarkiverse.langchain4j.lancedb.deployment;

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

public class LanceDbDefaultAndNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.api-key", "test-api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.database", "test-db")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.dimension", "384")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.named-store.api-key", "test-api-key")
            .overrideConfigKey("quarkus.langchain4j.lancedb.named-store.database", "named-db")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.named-store.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.lancedb.named-store.table-name", "named_embeddings");

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
