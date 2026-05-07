package io.quarkiverse.langchain4j.milvus.runtime;

import java.util.function.Function;

import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class MilvusRecorder {
    private final RuntimeValue<MilvusRuntimeConfig> runtimeConfig;

    public MilvusRecorder(RuntimeValue<MilvusRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Function<SyntheticCreationalContext<MilvusEmbeddingStore>, MilvusEmbeddingStore> embeddingStoreFunction(
            String storeName) {
        return new Function<>() {
            @Override
            public MilvusEmbeddingStore apply(SyntheticCreationalContext<MilvusEmbeddingStore> context) {
                MilvusStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);
                if (storeConfig.dimension().isEmpty()) {
                    throw new ConfigValidationException(createDimensionConfigProblems(storeName));
                }
                return new MilvusEmbeddingStore.Builder()
                        .host(storeConfig.host())
                        .port(storeConfig.port())
                        .collectionName(storeConfig.collectionName())
                        .idFieldName(storeConfig.primaryField())
                        .textFieldName(storeConfig.textField())
                        .metadataFieldName(storeConfig.metadataField())
                        .vectorFieldName(storeConfig.vectorField())
                        .dimension(storeConfig.dimension().get())
                        .indexType(storeConfig.indexType())
                        .metricType(storeConfig.metricType())
                        .token(storeConfig.token().orElse(null))
                        .username(storeConfig.username().orElse(null))
                        .password(storeConfig.password().orElse(null))
                        .consistencyLevel(storeConfig.consistencyLevel())
                        .retrieveEmbeddingsOnSearch(true)
                        .databaseName(storeConfig.dbName())
                        .build();
            }
        };
    }

    MilvusStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        if (NamedConfigUtil.isDefault(storeName)) {
            return runtimeConfig.getValue().defaultConfig();
        }
        return runtimeConfig.getValue().namedConfig().get(storeName);
    }

    private ConfigValidationException.Problem[] createDimensionConfigProblems(String storeName) {
        return new ConfigValidationException.Problem[] {
                new ConfigValidationException.Problem(String.format(
                        "SRCFG00014: The config property quarkus.langchain4j.milvus%sdimension is required but it could not be found in any config source",
                        NamedConfigUtil.isDefault(storeName) ? "."
                                : ("." + storeName + ".")))
        };
    }
}
