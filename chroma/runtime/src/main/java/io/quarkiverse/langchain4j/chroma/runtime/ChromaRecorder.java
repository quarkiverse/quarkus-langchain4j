package io.quarkiverse.langchain4j.chroma.runtime;

import java.time.Duration;
import java.util.function.Supplier;

import io.quarkiverse.langchain4j.chroma.ChromaEmbeddingStore;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ChromaRecorder {
    public Supplier<ChromaEmbeddingStore> chromaStoreSupplier(ChromaConfig config) {
        return new Supplier<>() {
            @Override
            public ChromaEmbeddingStore get() {
                return new ChromaEmbeddingStore(config.url(),
                        config.collectionName(),
                        config.timeout().orElse(Duration.ofSeconds(5)));
            }
        };
    }
}
