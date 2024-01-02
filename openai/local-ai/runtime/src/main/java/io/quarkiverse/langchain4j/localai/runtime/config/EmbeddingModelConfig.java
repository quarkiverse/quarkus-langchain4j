package io.quarkiverse.langchain4j.localai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Model name to use
     */
    Optional<String> modelName();
}
