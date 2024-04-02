package io.quarkiverse.langchain4j.pgvector;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkiverse.langchain4j.pgvector.runtime.PgVectorEmbeddingStoreConfig.MetadataConfig;

/**
 * PGVector EmbeddingStore Implementation
 * <p>
 * Same as langChain4J DataSourcePgVectorEmbeddingStore but with quarkus datasource and config
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
        super(datasource, table, dimension, useIndex, indexListSize, createTable, dropTableFirst, metadataConfig);
    }

    /**
     * Connection does not need to be configured each time like in standalone mode.
     * Quarkus langchain does the configuration at startup in PgVectorConfigBuilderCustomizer
     *
     * @return the datasource connection.
     * @throws SQLException exception
     */
    @Override
    public Connection getConnection() throws SQLException {
        return datasource.getConnection();
    }

}
