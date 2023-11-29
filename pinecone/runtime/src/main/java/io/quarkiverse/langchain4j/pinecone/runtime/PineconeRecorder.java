package io.quarkiverse.langchain4j.pinecone.runtime;

import java.time.Duration;
import java.util.function.Supplier;

import io.quarkiverse.langchain4j.pinecone.PineconeEmbeddingStore;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class PineconeRecorder {

    public Supplier<PineconeEmbeddingStore> pineconeStoreSupplier(PineconeConfig config) {
        return new Supplier<>() {
            @Override
            public PineconeEmbeddingStore get() {
                return new PineconeEmbeddingStore(config.apiKey(),
                        config.indexName(),
                        config.projectId(),
                        config.environment(),
                        config.namespace().orElse(null),
                        config.textFieldName(),
                        config.timeout().orElse(Duration.ofSeconds(5)),
                        config.dimension().orElse(null));
            }
        };
    }
}
