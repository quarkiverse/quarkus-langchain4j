package io.quarkiverse.langchain4j.ollama.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelFixedRuntimeConfig {

    /**
     * Model to use. According to <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md#model-names">Ollama
     * docs</a>,
     * the default value is {@code llama3}
     */
    @WithDefault("llama3")
    String modelId();
}
