package io.quarkiverse.langchain4j.deployment.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ScoringModelConfig {
    /**
     * The model provider to use
     */
    Optional<String> provider();
}
