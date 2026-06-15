package io.quarkiverse.langchain4j.google.genai.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelBuildConfig {

    /**
     * Whether the embedding model provider is enabled
     */
    @ConfigDocDefault("true")
    @WithDefault("true")
    Optional<Boolean> enabled();
}
