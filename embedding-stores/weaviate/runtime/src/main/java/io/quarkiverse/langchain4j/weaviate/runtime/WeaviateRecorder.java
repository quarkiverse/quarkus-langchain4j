package io.quarkiverse.langchain4j.weaviate.runtime;

import java.util.function.Supplier;

import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;

@Recorder
public class WeaviateRecorder {
    private final RuntimeValue<WeaviateRuntimeConfig> runtimeConfig;

    public WeaviateRecorder(RuntimeValue<WeaviateRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<WeaviateClient> weaviateClientSupplier() {
        return new Supplier<>() {
            @Override
            public WeaviateClient get() {
                return new WeaviateClient(new Config(runtimeConfig.getValue().scheme(),
                        runtimeConfig.getValue().host() + ":" + runtimeConfig.getValue().port()));
            }
        };
    }

    public Supplier<WeaviateEmbeddingStore> weaviateStoreSupplier() {
        return new Supplier<>() {
            @Override
            public WeaviateEmbeddingStore get() {
                return WeaviateEmbeddingStore.builder()
                        .apiKey(runtimeConfig.getValue().apiKey().orElse(null))
                        .scheme(runtimeConfig.getValue().scheme())
                        .host(runtimeConfig.getValue().host())
                        .port(runtimeConfig.getValue().port())
                        .objectClass(runtimeConfig.getValue().objectClass())
                        .textFieldName(runtimeConfig.getValue().textFieldName())
                        .avoidDups(runtimeConfig.getValue().avoidDups())
                        .consistencyLevel(runtimeConfig.getValue().consistencyLevel().toString())
                        .metadataKeys(runtimeConfig.getValue().metadata().keys())
                        .metadataFieldName(runtimeConfig.getValue().metadata().fieldName())
                        .build();
            }
        };
    }
}
