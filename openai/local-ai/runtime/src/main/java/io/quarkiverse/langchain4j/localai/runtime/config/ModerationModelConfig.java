package io.quarkiverse.langchain4j.localai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ModerationModelConfig {

    /**
     * Model name to use
     */
    Optional<String> modelName();
}
