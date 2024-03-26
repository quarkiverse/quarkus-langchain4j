package io.quarkiverse.langchain4j.pgvector;

import java.sql.SQLException;
import java.sql.Statement;

import io.quarkiverse.langchain4j.pgvector.runtime.PgVectorEmbeddingStoreConfig;
import io.quarkus.logging.Log;

public class JSONBMetadataHandler extends JSONMetadataHandler {

    final String indexType;

    public JSONBMetadataHandler(PgVectorEmbeddingStoreConfig.MetadataConfig config) {
        super(config);
        if (!this.columnDefinition().toLowerCase().contains("jsonb")) {
            throw new RuntimeException("Your column definition should contains JSONB Type");
        }
        indexType = config.indexType();
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) {
        for (String str : this.indexes) {
            String index = str.trim();
            String indexName = formatIndex(index);
            try {
                String indexSql = String.format("create index %s_%s on %s USING %s ( %s )",
                        table, indexName, table, indexType, index);
                Log.debug(indexSql);
                statement.executeUpdate(indexSql);
            } catch (SQLException e) {
                throw new RuntimeException(String.format("Cannot create index %s: %s", index, e));
            }
        }
    }

    String formatIndex(String index) {
        // (metadata_b->'name')
        String indexName;
        if (index.contains("->")) {
            indexName = columnName + "_" + index.substring(index.indexOf("->") + 3, index.length() - 1)
                    .trim().replaceAll("'", "");
        } else {
            indexName = index;
        }
        return indexName;
    }
}
