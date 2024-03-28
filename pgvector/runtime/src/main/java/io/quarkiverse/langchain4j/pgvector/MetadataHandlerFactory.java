package io.quarkiverse.langchain4j.pgvector;

import io.quarkiverse.langchain4j.pgvector.runtime.PgVectorEmbeddingStoreConfig;

public class MetadataHandlerFactory {

    public static MetadataHandler get(PgVectorEmbeddingStoreConfig.MetadataConfig config) {
        return switch (config.type()) {
            case "JSON" -> new JSONMetadataHandler(config);
            case "JSONB" -> new JSONBMetadataHandler(config);
            case "COLUMNS" -> new ColumnsMetadataHandler(config);
            default -> throw new RuntimeException(String.format("Type %s not handled.", config.type()));
        };
    }

}
