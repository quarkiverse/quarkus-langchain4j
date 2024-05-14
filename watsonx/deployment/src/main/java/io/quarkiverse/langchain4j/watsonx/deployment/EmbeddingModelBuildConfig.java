package io.quarkiverse.langchain4j.watsonx.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface EmbeddingModelBuildConfig {

    /**
     * Whether the embedding model should be enabled.
     */
    @ConfigDocDefault("true")
    Optional<Boolean> enabled();
}
