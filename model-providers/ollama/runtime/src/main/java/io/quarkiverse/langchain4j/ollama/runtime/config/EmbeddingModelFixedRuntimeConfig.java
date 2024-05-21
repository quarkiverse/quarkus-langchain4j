package io.quarkiverse.langchain4j.ollama.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelFixedRuntimeConfig {

    /**
     * Model to use. According to <a href="https://github.com/jmorganca/ollama/blob/main/docs/api.md#model-names">Ollama
     * docs</a>, the default value is {@code nomic-embed-text}
     */
    @WithDefault("nomic-embed-text")
    String modelId();
}
