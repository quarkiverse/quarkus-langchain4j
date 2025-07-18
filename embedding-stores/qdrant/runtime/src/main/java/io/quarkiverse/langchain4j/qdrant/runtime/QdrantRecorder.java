package io.quarkiverse.langchain4j.qdrant.runtime;

import java.util.function.Supplier;

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class QdrantRecorder {
    private final RuntimeValue<QdrantRuntimeConfig> runtimeConfig;

    public QdrantRecorder(RuntimeValue<QdrantRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<QdrantEmbeddingStore> qdrantStoreSupplier() {
        return new Supplier<>() {
            @Override
            public QdrantEmbeddingStore get() {
                return new QdrantEmbeddingStore.Builder()
                        .host(runtimeConfig.getValue().host())
                        .port(runtimeConfig.getValue().port())
                        .apiKey(runtimeConfig.getValue().apiKey().orElse(null))
                        .collectionName(runtimeConfig.getValue().collection().name())
                        .useTls(runtimeConfig.getValue().useTls())
                        .payloadTextKey(runtimeConfig.getValue().payloadTextKey())
                        .build();
            }
        };
    }
}
