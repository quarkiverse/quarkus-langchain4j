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
import io.quarkus.test.QuarkusUnitTest;

/**
 * Named stores must be discovered from any configured property, not only from the build-time
 * {@code datasource} one, which defaults to the default datasource.
 */
public class PgVectorNamedStoreWithoutDatasourceTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.datasource.db-kind", "postgresql")
            .overrideConfigKey("quarkus.datasource.devservices.image-name", "pgvector/pgvector:pg16")
            .overrideConfigKey("quarkus.langchain4j.pgvector.default-store-enabled", "false")
            .overrideConfigKey("quarkus.langchain4j.pgvector.products.table", "product_embeddings")
            .overrideConfigKey("quarkus.langchain4j.pgvector.products.dimension", "384")
            .overrideConfigKey("quarkus.langchain4j.pgvector.documents.table", "doc_embeddings")
            .overrideConfigKey("quarkus.langchain4j.pgvector.documents.dimension", "768");

    @Inject
    @EmbeddingStoreName("products")
    EmbeddingStore<TextSegment> productsEmbeddingStore;

    @Inject
    @EmbeddingStoreName("documents")
    EmbeddingStore<TextSegment> documentsEmbeddingStore;

    @Inject
    javax.sql.DataSource defaultDs;

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
        assertTableExists(productsEmbeddingStore, "product_embeddings");
    }

    @Test
    void testDocEmbeddingsTable() throws SQLException {
        assertTableExists(documentsEmbeddingStore, "doc_embeddings");
    }

    private void assertTableExists(EmbeddingStore<TextSegment> store, String tableName) throws SQLException {
        store.toString();
        try (Connection connection = defaultDs.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = '" + tableName + "')")) {
                    rs.next();
                    Assertions.assertThat(rs.getBoolean(1)).isTrue();
                }
            }
        }
    }
}
