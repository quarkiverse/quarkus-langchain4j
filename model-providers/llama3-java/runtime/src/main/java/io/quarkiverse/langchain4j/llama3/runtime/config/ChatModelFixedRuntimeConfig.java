package io.quarkiverse.langchain4j.llama3.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelFixedRuntimeConfig {

    /**
     * Model name to use
     */
    @WithDefault("mukel/Llama-3.2-3B-Instruct-GGUF")
    String modelName();

    /**
     * TODO
     */
    @WithDefault("Q4_0")
    String quantization();
}
