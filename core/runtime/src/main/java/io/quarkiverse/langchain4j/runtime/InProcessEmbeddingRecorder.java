package io.quarkiverse.langchain4j.runtime;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import dev.langchain4j.model.embedding.onnx.AbstractInProcessEmbeddingModel;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.mutiny.infrastructure.Infrastructure;

@Recorder
public class InProcessEmbeddingRecorder {
    @SuppressWarnings("unchecked")
    public Supplier<?> instantiate(String className) {
        return new Supplier<Object>() {
            @Override
            public Object get() {
                try {
                    Executor executor = Infrastructure.getDefaultWorkerPool();
                    Class<? extends AbstractInProcessEmbeddingModel> loaded = (Class<? extends AbstractInProcessEmbeddingModel>) InProcessEmbeddingRecorder.class
                            .getClassLoader().loadClass(className);
                    try {
                        return loaded.getDeclaredConstructor(Executor.class).newInstance(executor);
                    } catch (NoSuchMethodException e) {
                        Logger.getLogger(InProcessEmbeddingRecorder.class)
                                .warn("Cannot locate in-process embedding model's %s " +
                                        "constructor that takes a java.util.concurrent.Executor, " +
                                        "using the default constructor as fallback", className, e);
                        return loaded.getDeclaredConstructor().newInstance();
                    }
                } catch (Exception e) {
                    Logger.getLogger(InProcessEmbeddingRecorder.class)
                            .errorf("Failed to instantiate in-process embedding model %s", className, e);
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
