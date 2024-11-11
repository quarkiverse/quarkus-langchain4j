package io.quarkiverse.langchain4j.ollama.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelFixedRuntimeConfig {

    /**
     * Model to use
     */
    @WithDefault("llama3.2")
    String modelId();
}
