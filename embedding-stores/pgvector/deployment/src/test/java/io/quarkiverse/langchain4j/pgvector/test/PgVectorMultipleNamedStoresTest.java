package io.quarkiverse.langchain4j.pgvector.test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.inject.Inject;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.EmbeddingStoreName;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.QuarkusUnitTest;

public class PgVectorMultipleNamedStoresTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.pgvector.default-store-enabled", "false")
            .overrideConfigKey("quarkus.datasource.products-ds.devservices.image-name", "pgvector/pgvector:pg16")
            .overrideConfigKey("quarkus.datasource.products-ds.db-kind", "postgresql")
            .overrideConfigKey("quarkus.datasource.documents-ds.devservices.image-name", "pgvector/pgvector:pg16")
            .overrideConfigKey("quarkus.datasource.documents-ds.db-kind", "postgresql")
            .overrideConfigKey("quarkus.langchain4j.pgvector.products.datasource", "products-ds")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pgvector.products.dimension", "1536")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pgvector.products.table", "product_embeddings")
            .overrideConfigKey("quarkus.langchain4j.pgvector.documents.datasource", "documents-ds")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pgvector.documents.dimension", "768")
            .overrideRuntimeConfigKey("quarkus.langchain4j.pgvector.documents.table", "doc_embeddings");

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    EmbeddingStore<TextSegment> documentsEmbeddingStore;

    @Inject
    @DataSource("products-ds")
    javax.sql.DataSource productsDs;

    @Inject
    @DataSource("documents-ds")
    javax.sql.DataSource documentsDs;

    @Test
    void testBothNamed() {
        Assertions.assertThat(productsEmbeddingStore).isNotNull();
        Assertions.assertThat(documentsEmbeddingStore).isNotNull();
    }

    @Test
    void testNotSame() {
        Assertions.assertThat(productsEmbeddingStore).isNotSameAs(documentsEmbeddingStore);
    }

    @Test
    void testProductEmbeddingsTable() throws SQLException {
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

    @Test
    void testDocEmbeddingsTable() throws SQLException {
        documentsEmbeddingStore.toString();
        try (Connection connection = documentsDs.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'doc_embeddings')")) {
                    rs.next();
                    Assertions.assertThat(rs.getBoolean(1)).isTrue();
                }
            }
        }
    }
}
