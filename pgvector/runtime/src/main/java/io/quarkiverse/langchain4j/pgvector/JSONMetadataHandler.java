package io.quarkiverse.langchain4j.pgvector;

import java.sql.*;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.pgvector.runtime.PgVectorEmbeddingStoreConfig;
import io.quarkus.logging.Log;

/**
 * This class handle JSON and JSONB Filter mapping
 */
public class JSONMetadataHandler implements MetadataHandler {
    ObjectMapper objectMapper = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER;

    private static final TypeReference<Map<String, String>> typeReference = new TypeReference<>() {
    };
    final String columnDefinition;
    final String columnName;
    final JSONFilterMapper filterMapper;

    final List<String> indexes;

    public JSONMetadataHandler(PgVectorEmbeddingStoreConfig.MetadataConfig config) {
        this.columnDefinition = config.definition();
        if (this.columnDefinition().contains(",")) {
            throw new RuntimeException("Multiple columns definition are not allowed in JSON, JSONB Type");
        }
        this.columnName = this.columnDefinition.split(" ")[0];
        this.filterMapper = new JSONFilterMapper(columnName);
        this.indexes = config.indexes().orElse(Collections.emptyList());
    }

    @Override
    public String columnDefinition() {
        return columnDefinition;
    }

    @Override
    public String columnsNames() {
        return this.columnName;
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) {
        if (!this.indexes.isEmpty()) {
            throw new RuntimeException("Indexes are not allowed for JSON metadata, use JSONB instead");
        }
    }

    @Override
    public String whereClause(Filter filter) {
        String whereClause = filterMapper.map(filter);
        Log.debugf("Filter: %s SQL: %s", filter, whereClause);
        return whereClause;
    }

    @Override
    public Metadata fromResultSet(ResultSet resultSet) {
        try {
            String metadataJson = Optional.ofNullable(resultSet.getString(this.columnName)).orElse("{}");
            Map<String, String> metadataMap = objectMapper.readValue(metadataJson, typeReference);
            return new Metadata(new HashMap<>(metadataMap));
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer nbMetadataColumns() {
        return 1;
    }

    @Override
    public String insertClause() {
        return String.format("%s = EXCLUDED.%s", this.columnName, this.columnName);
    }

    @Override
    public void setMetadata(PreparedStatement upsertStmt, Integer parameterInitialIndex, Metadata metadata) {
        try {
            upsertStmt.setObject(parameterInitialIndex, objectMapper.writeValueAsString(metadata.asMap()), Types.OTHER);
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
