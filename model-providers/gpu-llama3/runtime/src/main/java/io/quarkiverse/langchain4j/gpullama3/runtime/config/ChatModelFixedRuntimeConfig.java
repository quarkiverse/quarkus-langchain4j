package io.quarkiverse.langchain4j.gpullama3.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelFixedRuntimeConfig {

    /**
     * Path to the model file (GGUF format)
     */
    @WithDefault("model.gguf")
    String modelPath();
}