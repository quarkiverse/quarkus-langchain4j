package io.quarkiverse.langchain4j.pgvector.test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.config.ConfigValidationException;

public class PgVectorNamedStoreMissingDimensionTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.db-kind=postgresql\n" +
                                    "quarkus.datasource.devservices.image-name=pgvector/pgvector:pg16\n" +
                                    "quarkus.langchain4j.pgvector.default-store-enabled=false\n" +
                                    "quarkus.langchain4j.pgvector.products.datasource=<default>\n" +
                                    "quarkus.langchain4j.pgvector.products.table=product_embeddings\n"),
                            "application.properties"));

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsEmbeddingStore;

    @Test
    void should_fail_with_missing_dimension() {
        assertThatThrownBy(() -> productsEmbeddingStore.toString())
                .hasCauseInstanceOf(ConfigValidationException.class);
    }
}
