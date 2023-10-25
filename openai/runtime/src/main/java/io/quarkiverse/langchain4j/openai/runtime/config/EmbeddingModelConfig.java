package io.quarkiverse.langchain4j.openai.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Model name to use
     */
    @WithDefault("text-embedding-ada-002")
    String modelName();
}
