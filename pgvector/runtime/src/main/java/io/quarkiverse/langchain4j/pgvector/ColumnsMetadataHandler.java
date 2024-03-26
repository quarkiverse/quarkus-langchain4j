package io.quarkiverse.langchain4j.pgvector;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import io.quarkiverse.langchain4j.pgvector.runtime.PgVectorEmbeddingStoreConfig;
import io.quarkus.logging.Log;

/**
 * This class handle
 */
public class ColumnsMetadataHandler implements MetadataHandler {

    final String columnsDefinition;
    final List<String> columnsName;
    final PgVectorFilterMapper filterMapper;

    final List<String> indexes;

    final String indexType;

    public ColumnsMetadataHandler(PgVectorEmbeddingStoreConfig.MetadataConfig config) {
        this.columnsDefinition = config.definition();
        this.columnsName = Arrays.stream(columnsDefinition.split(","))
                .map(d -> d.trim().split(" ")[0]).toList();
        this.filterMapper = new PgVectorFilterMapper();
        this.indexes = config.indexes().orElse(Collections.emptyList());
        this.indexType = config.indexType();
    }

    @Override
    public String columnDefinition() {
        return this.columnsDefinition;
    }

    @Override
    public String columnsNames() {
        return String.join(",", this.columnsName);
    }

    @Override
    public Integer nbMetadataColumns() {
        return this.columnsName.size();
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) {
        this.indexes.stream().map(String::trim)
                .forEach(index -> {
                    String indexSql = String.format("create index %s_%s on %s USING %s ( %s )",
                            table, index, table, indexType, index);
                    Log.debug(indexSql);
                    try {
                        statement.executeUpdate(indexSql);
                    } catch (SQLException e) {
                        throw new RuntimeException(String.format("Cannot create indexes %s: %s", index, e));
                    }
                });
    }

    @Override
    public String insertClause() {
        return this.columnsName.stream().map(c -> String.format("%s = EXCLUDED.%s", c, c))
                .collect(Collectors.joining(","));
    }

    @Override
    public void setMetadata(PreparedStatement upsertStmt, Integer parameterInitialIndex, Metadata metadata) {
        int i = 0;
        // only column names fields will be stored
        for (String c : this.columnsName) {
            try {
                Log.debugf("%d -> %s", parameterInitialIndex + i, metadata.get(c));
                upsertStmt.setObject(parameterInitialIndex + i, metadata.get(c), Types.OTHER);
                i++;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
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
            Map<String, Object> metadataMap = new HashMap<>();
            for (String c : this.columnsName) {
                if (resultSet.getObject(c) != null) {
                    metadataMap.put(c, resultSet.getObject(c));
                }
            }
            return new Metadata(metadataMap);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
