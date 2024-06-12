package io.quarkiverse.langchain4j.pgvector.test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verify use of a non-default postgresql datasource
 */
public class PgVectorDataSourceTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            // DevServicesConfigBuilderCustomizer overrides the image-name only
                            // for the default DS, so in this case we have to override it manually
                            "quarkus.datasource.embeddings-ds.devservices.image-name=pgvector/pgvector:pg16\n" +
                                    "quarkus.langchain4j.pgvector.datasource=embeddings-ds\n" +
                                    "quarkus.langchain4j.pgvector.dimension=1536\n" +
                                    "quarkus.datasource.embeddings-ds.db-kind=postgresql\n"),
                            "application.properties"));

    @io.quarkus.agroal.DataSource("embeddings-ds")
    DataSource ds;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Test
    public void verifyThatEmbeddingsTableIsCreated() throws SQLException {
        // make sure the store is initialized...
        embeddingStore.toString();
        try (Connection connection = ds.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'embeddings')")) {
                    rs.next();
                    Assertions.assertTrue(rs.getBoolean(1));
                }
            }
        }
    }
}
