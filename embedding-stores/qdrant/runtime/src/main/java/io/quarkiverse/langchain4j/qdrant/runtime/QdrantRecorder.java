package io.quarkiverse.langchain4j.qdrant.runtime;

import java.util.function.Supplier;

import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class QdrantRecorder {

    public Supplier<QdrantEmbeddingStore> qdrantStoreSupplier(QdrantRuntimeConfig config) {
        return new Supplier<>() {
            @Override
            public QdrantEmbeddingStore get() {
                return new QdrantEmbeddingStore.Builder()
                        .host(config.host())
                        .port(config.port())
                        .apiKey(config.apiKey().orElse(null))
                        .collectionName(config.collection().name())
                        .useTls(config.useTls())
                        .payloadTextKey(config.payloadTextKey())
                        .build();
            }
        };
    }
}
