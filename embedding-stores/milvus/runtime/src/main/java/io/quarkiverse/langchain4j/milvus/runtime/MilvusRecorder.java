package io.quarkiverse.langchain4j.milvus.runtime;

import java.util.function.Supplier;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class MilvusRecorder {
    private final RuntimeValue<MilvusRuntimeConfig> runtimeConfig;

    public MilvusRecorder(RuntimeValue<MilvusRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<MilvusEmbeddingStore> milvusStoreSupplier() {
        return new Supplier<>() {
            @Override
            public MilvusEmbeddingStore get() {
                return new MilvusEmbeddingStore.Builder()
                        .host(runtimeConfig.getValue().host())
                        .port(runtimeConfig.getValue().port())
                        .collectionName(runtimeConfig.getValue().collectionName())
                        .idFieldName(runtimeConfig.getValue().primaryField())
                        .textFieldName(runtimeConfig.getValue().textField())
                        .metadataFieldName(runtimeConfig.getValue().metadataField())
                        .vectorFieldName(runtimeConfig.getValue().vectorField())
                        .dimension(runtimeConfig.getValue().dimension().orElse(null))
                        .indexType(runtimeConfig.getValue().indexType())
                        .metricType(runtimeConfig.getValue().metricType())
                        .token(runtimeConfig.getValue().token().orElse(null))
                        .username(runtimeConfig.getValue().username().orElse(null))
                        .password(runtimeConfig.getValue().password().orElse(null))
                        .consistencyLevel(runtimeConfig.getValue().consistencyLevel())
                        .retrieveEmbeddingsOnSearch(true)
                        .databaseName(runtimeConfig.getValue().dbName())
                        .build();
            }
        };
    }
}
