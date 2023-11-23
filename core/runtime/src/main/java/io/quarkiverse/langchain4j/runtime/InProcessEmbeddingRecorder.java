package io.quarkiverse.langchain4j.runtime;

import java.util.function.Supplier;

import org.jboss.logging.Logger;

import dev.langchain4j.model.embedding.AbstractInProcessEmbeddingModel;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class InProcessEmbeddingRecorder {
    @SuppressWarnings("unchecked")
    public Supplier<?> instantiate(String className) {
        return () -> {
            try {
                Class<? extends AbstractInProcessEmbeddingModel> loaded = (Class<? extends AbstractInProcessEmbeddingModel>) InProcessEmbeddingRecorder.class
                        .getClassLoader().loadClass(className);
                return loaded.getConstructor().newInstance();
            } catch (Exception e) {
                Logger.getLogger(InProcessEmbeddingRecorder.class)
                        .errorf("Failed to instantiate in-process embedding model %s", className, e);
                throw new RuntimeException(e);
            }
        };
    }
}
