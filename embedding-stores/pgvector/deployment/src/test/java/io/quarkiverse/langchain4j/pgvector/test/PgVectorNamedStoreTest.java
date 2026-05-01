package io.quarkiverse.langchain4j.pgvector.test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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

public class PgVectorNamedStoreTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            "quarkus.datasource.db-kind=postgresql\n" +
                                    "quarkus.datasource.devservices.image-name=pgvector/pgvector:pg16\n" +
                                    "quarkus.datasource.products-ds.devservices.image-name=pgvector/pgvector:pg16\n" +
                                    "quarkus.datasource.products-ds.db-kind=postgresql\n" +
                                    "quarkus.langchain4j.pgvector.dimension=384\n" +
                                    "quarkus.langchain4j.pgvector.products.datasource=products-ds\n" +
                                    "quarkus.langchain4j.pgvector.products.dimension=1536\n" +
                                    "quarkus.langchain4j.pgvector.products.table=product_embeddings\n"),
                            "application.properties"));

    @Inject
    EmbeddingStore<TextSegment> defaultEmbeddingStore;

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsEmbeddingStore;

    @io.quarkus.agroal.DataSource("products-ds")
    javax.sql.DataSource productsDs;

    @Test
    void should_injectDefaultStore() {
        Assertions.assertThat(defaultEmbeddingStore).isNotNull();
    }

    @Test
    void should_injectNamedStore() {
        Assertions.assertThat(productsEmbeddingStore).isNotNull();
    }

    @Test
    void should_injectDifferentStoreInstances() {
        Assertions.assertThat(defaultEmbeddingStore).isNotSameAs(productsEmbeddingStore);
    }

    @Test
    void should_createNamedStoreTable() throws SQLException {
        productsEmbeddingStore.toString();
        try (Connection connection = productsDs.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'product_embeddings')")) {
                    rs.next();
                    Assertions.assertThat(rs.getBoolean(1)).isTrue();
                }
            }
        }
    }
}
