package io.quarkiverse.langchain4j.pgvector;

import io.quarkus.logging.Log;

class JSONFilterMapper extends PgVectorFilterMapper {
    final String metadataColumn;

    public JSONFilterMapper(String metadataColumn) {
        this.metadataColumn = metadataColumn;
    }

    String formatKeyAsText(String key) {
        return metadataColumn + "->>'" + key + "'";
    }

    String formatKey(String key, Class<?> valueType) {
        Log.debugf("formatKey %s -> %s", key, valueType);
        return String.format("(%s->>'%s')::%s", metadataColumn, key, SQL_TYPE_MAP.get(valueType));
    }

}
