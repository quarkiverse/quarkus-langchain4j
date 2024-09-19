package io.quarkiverse.langchain4j.ollama.runtime.config;

import io.smallrye.config.RelocateConfigSourceInterceptor;

public class ModelIdConfigRelocateInterceptor extends RelocateConfigSourceInterceptor {
    public ModelIdConfigRelocateInterceptor() {
        super(ModelIdToModelNameFunction.INSTANCE);
    }
}
