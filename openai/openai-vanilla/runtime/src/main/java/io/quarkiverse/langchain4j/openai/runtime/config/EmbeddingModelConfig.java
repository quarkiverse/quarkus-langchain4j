package io.quarkiverse.langchain4j.openai.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Embedding Model name to use
     * 
     * See https://platform.openai.com/docs/guides/embeddings/embedding-models for a list of available models.
     */
    @WithDefault("text-embedding-ada-002")
    String modelName();
}
