package io.quarkiverse.langchain4j.chroma.runtime;

import java.time.Duration;
import java.util.function.Supplier;

import io.quarkiverse.langchain4j.chroma.ChromaEmbeddingStore;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ChromaRecorder {
    private final RuntimeValue<ChromaConfig> runtimeConfig;

    public ChromaRecorder(RuntimeValue<ChromaConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<ChromaEmbeddingStore> chromaStoreSupplier() {
        return new Supplier<>() {
            @Override
            public ChromaEmbeddingStore get() {
                return new ChromaEmbeddingStore(runtimeConfig.getValue().url(),
                        runtimeConfig.getValue().collectionName(),
                        runtimeConfig.getValue().timeout().orElse(Duration.ofSeconds(5)),
                        runtimeConfig.getValue().logRequests().orElse(false),
                        runtimeConfig.getValue().logResponses().orElse(false));
            }
        };
    }
}
