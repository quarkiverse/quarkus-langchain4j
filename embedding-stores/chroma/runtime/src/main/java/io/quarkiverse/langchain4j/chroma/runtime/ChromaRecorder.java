package io.quarkiverse.langchain4j.chroma.runtime;

import java.time.Duration;
import java.util.function.Supplier;

import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
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
                ChromaEmbeddingStore.Builder builder = ChromaEmbeddingStore.builder();
                builder.apiVersion(runtimeConfig.getValue().apiVersion());
                builder.collectionName(runtimeConfig.getValue().collectionName());
                builder.logRequests(runtimeConfig.getValue().logRequests().orElse(false));
                builder.logResponses(runtimeConfig.getValue().logResponses().orElse(false));
                builder.timeout(runtimeConfig.getValue().timeout().orElse(Duration.ofSeconds(5)));
                builder.baseUrl(runtimeConfig.getValue().url());
                return builder.build();
            }
        };
    }
}
