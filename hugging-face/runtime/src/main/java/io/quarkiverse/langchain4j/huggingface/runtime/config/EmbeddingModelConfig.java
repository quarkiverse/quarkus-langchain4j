package io.quarkiverse.langchain4j.huggingface.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Model to use
     */
    @WithDefault("sentence-transformers/all-MiniLM-L6-v2")
    String modelId();

    /**
     * TODO
     */
    @WithDefault("true")
    Boolean waitForModel();
}
