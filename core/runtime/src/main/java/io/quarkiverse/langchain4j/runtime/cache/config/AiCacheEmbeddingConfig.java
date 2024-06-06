package io.quarkiverse.langchain4j.runtime.cache.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface AiCacheEmbeddingConfig {

    /**
     * Add a prefix to each \"query\" value before performing the embedding operation for the similarity search.
     */
    Optional<String> queryPrefix();

    /**
     * Add a prefix to each \"response\" value before performing the embedding operation.
     */
    Optional<String> passagePrefix();
}
