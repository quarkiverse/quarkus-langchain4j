package io.quarkiverse.langchain4j.oracle.test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.test.QuarkusUnitTest;

public class OracleDataSourceTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.embeddingsds.devservices.image-name", "gvenzl/oracle-free:23-slim")
            .overrideConfigKey("quarkus.datasource.embeddingsds.db-kind", "oracle")
            .overrideConfigKey("quarkus.langchain4j.oracle.datasource", "embeddingsds")
            .overrideRuntimeConfigKey("quarkus.langchain4j.oracle.create-option", "CREATE_OR_REPLACE")
            .overrideConfigKey("quarkus.class-loading.parent-first-artifacts", "ai.djl.huggingface:tokenizers");

    @DataSource("embeddingsds")
    AgroalDataSource ds;

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Test
    public void verifyThatEmbeddingsTableIsCreated() throws SQLException {
        embeddingStore.toString();
        try (Connection connection = ds.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                try (ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM user_tables WHERE table_name = 'EMBEDDINGS'")) {
                    rs.next();
                    Assertions.assertTrue(rs.getInt(1) > 0);
                }
            }
        }
    }
}
