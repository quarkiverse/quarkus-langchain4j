package io.quarkiverse.langchain4j.pgvector.test;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
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
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.db-kind=postgresql\n" +
                                    "quarkus.datasource.devservices.image-name=pgvector/pgvector:pg16\n" +
                                    "quarkus.datasource.named-ds.devservices.image-name=pgvector/pgvector:pg16\n" +
                                    "quarkus.datasource.named-ds.db-kind=postgresql\n" +
                                    "quarkus.langchain4j.pgvector.dimension=384\n" +
                                    "quarkus.langchain4j.pgvector.named-store.datasource=named-ds\n" +
                                    "quarkus.langchain4j.pgvector.named-store.dimension=1536\n" +
                                    "quarkus.langchain4j.pgvector.named-store.table=named_embeddings\n"),
                            "application.properties"));

    @Inject
    EmbeddingStore<TextSegment> defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("named-store")
    EmbeddingStore<TextSegment> namedEmbeddingStore;

    @Test
    void should_injectDefaultStore() {
        Assertions.assertThat(defaultEmbeddingStore).isNotNull();
    }

    @Test
    void should_injectNamedStore() {
        Assertions.assertThat(namedEmbeddingStore).isNotNull();
    }

    @Test
    void should_injectDifferentStoreInstances() {
        Assertions.assertThat(defaultEmbeddingStore).isNotSameAs(namedEmbeddingStore);
    }
}
