package io.quarkiverse.langchain4j.milvus.runtime;

import java.util.function.Supplier;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class MilvusRecorder {

    public Supplier<MilvusEmbeddingStore> milvusStoreSupplier(MilvusRuntimeConfig config) {
        return new Supplier<>() {
            @Override
            public MilvusEmbeddingStore get() {
                return new MilvusEmbeddingStore.Builder()
                        .host(config.host())
                        .port(config.port())
                        .collectionName(config.collectionName())
                        .idFieldName(config.primaryField())
                        .textFieldName(config.textField())
                        .metadataFieldName(config.metadataField())
                        .vectorFieldName(config.vectorField())
                        .dimension(config.dimension().orElse(null))
                        .indexType(config.indexType())
                        .metricType(config.metricType())
                        .token(config.token().orElse(null))
                        .username(config.username().orElse(null))
                        .password(config.password().orElse(null))
                        .consistencyLevel(config.consistencyLevel())
                        .retrieveEmbeddingsOnSearch(true)
                        .databaseName(config.dbName())
                        .build();
            }
        };
    }
}
