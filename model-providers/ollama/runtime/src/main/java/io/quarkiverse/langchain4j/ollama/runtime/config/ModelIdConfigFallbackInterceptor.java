package io.quarkiverse.langchain4j.ollama.runtime.config;

import io.smallrye.config.FallbackConfigSourceInterceptor;

public class ModelIdConfigFallbackInterceptor extends FallbackConfigSourceInterceptor {

    public ModelIdConfigFallbackInterceptor() {
        super(ModelIdToModelNameFunction.INSTANCE);
    }

}
