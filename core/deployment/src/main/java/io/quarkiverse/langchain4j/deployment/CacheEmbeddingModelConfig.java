package io.quarkiverse.langchain4j.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface CacheEmbeddingModelConfig {

    /**
     * Name of the embedding model to use in the semantic cache.
     */
    Optional<String> name();
}
