package io.quarkiverse.langchain4j.pgvector;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;

import javax.sql.DataSource;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import io.quarkiverse.langchain4j.pgvector.runtime.PgVectorEmbeddingStoreConfig.MetadataConfig;

/**
 * Quarkus PGVector EmbeddingStore Implementation
 * <p>
 * Same as langChain4J PgVectorEmbeddingStore but with quarkus datasource and config
 * getConnection() does not need anymore to add PgVector datatype for each connection
 * because it's done at datasource configuration in deployment phase.
 */
public class PgVectorEmbeddingStore extends dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore
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
    public PgVectorEmbeddingStore(
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
    protected PgVectorEmbeddingStore() {
    }

    // Needed for doc generation, ascii doctor generates twice the properties, one for each MetadataConfig
    // And build fail.
    private static dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig toLangchainMetadataConfig(
            MetadataConfig metadataConfig) {
        return DefaultMetadataStorageConfig.builder()
                .storageMode(metadataConfig.storageMode())
                .columnDefinitions(metadataConfig.columnDefinitions())
                .indexes(metadataConfig.indexes().orElse(Collections.emptyList()))
                .indexType(metadataConfig.indexType())
                .build();
    }

    /**
     * return the connection as it is. pgvector settings as been added to connection at creation
     *
     * @return already configured connection with pgvector type and extension
     * @throws SQLException exception
     */
    protected Connection getConnection() throws SQLException {
        return datasource.getConnection();
    }
}
