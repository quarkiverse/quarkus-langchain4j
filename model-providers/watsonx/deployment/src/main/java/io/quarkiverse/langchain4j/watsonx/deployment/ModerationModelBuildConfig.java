package io.quarkiverse.langchain4j.watsonx.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface ModerationModelBuildConfig {

    /**
     * Whether the moderation model should be enabled.
     */
    @ConfigDocDefault("true")
    Optional<Boolean> enabled();
}
