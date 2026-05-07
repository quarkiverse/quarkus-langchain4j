package io.quarkiverse.langchain4j.pgvector.test;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;

public class PgVectorDefaultAndNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.datasource.db-kind", "postgresql")
            .overrideConfigKey("quarkus.datasource.devservices.image-name", "pgvector/pgvector:pg16")
            .overrideConfigKey("quarkus.datasource.named-ds.devservices.image-name", "pgvector/pgvector:pg16")
            .overrideConfigKey("quarkus.datasource.named-ds.db-kind", "postgresql")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pgvector.dimension", "384")
            .overrideConfigKey("quarkus.langchain4j.pgvector.named-store.datasource", "named-ds")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pgvector.named-store.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pgvector.named-store.table", "named_embeddings");

    @Inject
    EmbeddingStore<TextSegment> defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("named-store")
    EmbeddingStore<TextSegment> namedEmbeddingStore;

    @Test
    void testDefault() {
        Assertions.assertThat(defaultEmbeddingStore).isNotNull();
    }

    @Test
    void testNamed() {
        Assertions.assertThat(namedEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        Assertions.assertThat(defaultEmbeddingStore).isNotSameAs(namedEmbeddingStore);
    }
}
