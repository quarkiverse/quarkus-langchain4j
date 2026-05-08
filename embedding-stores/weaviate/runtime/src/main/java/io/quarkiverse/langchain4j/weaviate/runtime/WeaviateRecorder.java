package io.quarkiverse.langchain4j.weaviate.runtime;

import java.util.function.Supplier;

import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;

@Recorder
public class WeaviateRecorder {
    private final RuntimeValue<WeaviateEmbeddingStoreConfig> runtimeConfig;

    public WeaviateRecorder(RuntimeValue<WeaviateEmbeddingStoreConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<WeaviateClient> weaviateClientSupplier(String storeName) {
        WeaviateStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);
        return () -> new WeaviateClient(new Config(storeConfig.scheme(), storeConfig.host() + ":" + storeConfig.port()));
    }

    public Supplier<WeaviateEmbeddingStore> weaviateStoreSupplier(String storeName) {
        WeaviateStoreRuntimeConfig storeConfig = correspondingStoreConfig(storeName);
        return () -> WeaviateEmbeddingStore.builder()
                .apiKey(storeConfig.apiKey().orElse(null))
                .scheme(storeConfig.scheme())
                .host(storeConfig.host())
                .port(storeConfig.port())
                .objectClass(storeConfig.objectClass())
                .textFieldName(storeConfig.textFieldName())
                .avoidDups(storeConfig.avoidDups())
                .consistencyLevel(storeConfig.consistencyLevel().toString())
                .metadataKeys(storeConfig.metadata().keys())
                .metadataFieldName(storeConfig.metadata().fieldName())
                .build();
    }

    WeaviateStoreRuntimeConfig correspondingStoreConfig(String storeName) {
        if (NamedConfigUtil.isDefault(storeName)) {
            return runtimeConfig.getValue().defaultConfig();
        }
        WeaviateStoreRuntimeConfig namedConfig = runtimeConfig.getValue().namedConfig().get(storeName);
        if (namedConfig != null) {
            return namedConfig;
        }
        return runtimeConfig.getValue().defaultConfig();
    }
}
