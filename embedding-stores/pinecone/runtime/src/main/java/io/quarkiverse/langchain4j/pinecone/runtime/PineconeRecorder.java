package io.quarkiverse.langchain4j.pinecone.runtime;

import java.time.Duration;
import java.util.function.Supplier;

import io.quarkiverse.langchain4j.pinecone.PineconeEmbeddingStore;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class PineconeRecorder {
    private final RuntimeValue<PineconeConfig> runtimeConfig;

    public PineconeRecorder(RuntimeValue<PineconeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<PineconeEmbeddingStore> pineconeStoreSupplier() {
        return new Supplier<>() {
            @Override
            public PineconeEmbeddingStore get() {
                return new PineconeEmbeddingStore(runtimeConfig.getValue().apiKey(),
                        runtimeConfig.getValue().indexName(),
                        runtimeConfig.getValue().projectId(),
                        runtimeConfig.getValue().environment(),
                        runtimeConfig.getValue().namespace().orElse(null),
                        runtimeConfig.getValue().textFieldName(),
                        runtimeConfig.getValue().timeout().orElse(Duration.ofSeconds(5)),
                        runtimeConfig.getValue().dimension().orElse(null),
                        runtimeConfig.getValue().podType(),
                        runtimeConfig.getValue().indexReadinessTimeout().orElse(Duration.ofMinutes(1)));
            }
        };
    }
}
