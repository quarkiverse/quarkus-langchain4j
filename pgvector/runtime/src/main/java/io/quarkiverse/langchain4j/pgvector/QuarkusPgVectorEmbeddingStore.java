package io.quarkiverse.langchain4j.pgvector;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

import javax.sql.DataSource;

import com.pgvector.PGvector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataConfig;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import io.quarkiverse.langchain4j.pgvector.runtime.PgVectorEmbeddingStoreConfig.MetadataConfig;
import io.quarkus.logging.Log;

/**
 * Quarkus PGVector EmbeddingStore Implementation
 * <p>
 * Same as langChain4J PgVectorEmbeddingStore but with quarkus datasource and config
 * getConnection() does not need anymore to add PgVector datatype for each connection
 * because it's done at datasource configuration in deployment phase.
 */
public class QuarkusPgVectorEmbeddingStore extends PgVectorEmbeddingStore
        implements EmbeddingStore<TextSegment> {

    /**
     * Constructor used by quarkus deployment with quarkus datasource and config.
     *
     * @param datasource The datasource
     * @param table The database table
     * @param dimension The vector dimension
     * @param useIndex Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param indexListSize The IVFFlat number of lists
     * @param createTable Should create table automatically
     * @param dropTableFirst Should drop table first, usually for testing
     * @param metadataConfig The {@link MetadataConfig} config.
     */
    public QuarkusPgVectorEmbeddingStore(
            DataSource datasource,
            String table,
            Integer dimension,
            Boolean useIndex,
            Integer indexListSize,
            Boolean createTable,
            Boolean dropTableFirst,
            MetadataConfig metadataConfig) {
        super(datasource, table, dimension, useIndex, indexListSize, createTable, dropTableFirst,
                toLangchainMetadataConfig(metadataConfig));
    }

    /**
     * Default constructor for CDI
     */
    @SuppressWarnings("unused")
    protected QuarkusPgVectorEmbeddingStore() {
    }

    // Needed for doc generation, ascii doctor generates twice the properties, one for each MetadataConfig
    // And build fail.
    private static dev.langchain4j.store.embedding.pgvector.MetadataConfig toLangchainMetadataConfig(
            MetadataConfig metadataConfig) {
        return DefaultMetadataConfig.builder()
                .type(metadataConfig.type())
                .definition(metadataConfig.definition())
                .indexes(metadataConfig.indexes().orElse(Collections.emptyList()))
                .indexType(metadataConfig.indexType())
                .build();
    }

    // For the moment just overriding for a specific error message
    // But at some point quarkus should initialize correctly the connection(s) at datasource initialization.
    // For example for production, an application SQL script creates the database,
    // no needs to add the extension creation here. And we will be able to add the properties:
    //"quarkus.datasource.jdbc.additional-jdbc-properties.datatype.vector", "com.pgvector.PGvector" in
    protected Connection getConnection() throws SQLException {
        Connection connection = datasource.getConnection();
        // Find a way to do the following code in connection initialization.
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
        } catch (SQLException exception) {
            if (exception.getMessage().contains("could not open extension control file")) {
                Log.error(
                        "The PostgreSQL server does not seem to support pgvector."
                                + "If using containers we suggest to use pgvector/pgvector:pg16 image");
            } else {
                throw exception;
            }
        }
        PGvector.addVectorType(connection);
        return connection;
    }

}
