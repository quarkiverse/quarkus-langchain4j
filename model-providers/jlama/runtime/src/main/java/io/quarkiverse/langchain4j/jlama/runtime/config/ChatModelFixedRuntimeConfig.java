package io.quarkiverse.langchain4j.jlama.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelFixedRuntimeConfig {

    /**
     * Model name to use
     */
    @WithDefault("tjake/TinyLlama-1.1B-Chat-v1.0-Jlama-Q4")
    String modelName();
}
