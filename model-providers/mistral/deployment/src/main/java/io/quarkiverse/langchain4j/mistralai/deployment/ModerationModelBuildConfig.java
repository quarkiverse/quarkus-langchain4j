package io.quarkiverse.langchain4j.mistralai.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;

public interface ModerationModelBuildConfig {

    /**
     * Whether the model should be enabled
     */
    @ConfigDocDefault("true")
    Optional<Boolean> enabled();

}
