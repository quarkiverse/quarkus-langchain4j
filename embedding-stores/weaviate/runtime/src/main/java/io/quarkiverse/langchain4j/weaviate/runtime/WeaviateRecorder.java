package io.quarkiverse.langchain4j.weaviate.runtime;

import java.util.function.Supplier;

import dev.langchain4j.store.embedding.weaviate.WeaviateEmbeddingStore;
import io.quarkus.runtime.annotations.Recorder;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;

@Recorder
public class WeaviateRecorder {

    public Supplier<WeaviateClient> weaviateClientSupplier(WeaviateRuntimeConfig config) {
        return new Supplier<>() {
            @Override
            public WeaviateClient get() {
                return new WeaviateClient(new Config(config.scheme(), config.host() + ":" + config.port()));
            }
        };
    }

    public Supplier<WeaviateEmbeddingStore> weaviateStoreSupplier(WeaviateRuntimeConfig config) {
        return new Supplier<>() {
            @Override
            public WeaviateEmbeddingStore get() {
                return WeaviateEmbeddingStore.builder()
                        .apiKey(config.apiKey().orElse(null))
                        .scheme(config.scheme())
                        .host(config.host())
                        .port(config.port())
                        .objectClass(config.objectClass())
                        .textFieldName(config.textFieldName())
                        .avoidDups(config.avoidDups())
                        .consistencyLevel(config.consistencyLevel().toString())
                        .metadataKeys(config.metadata().keys())
                        .metadataFieldName(config.metadata().fieldName())
                        .build();
            }
        };
    }
}
