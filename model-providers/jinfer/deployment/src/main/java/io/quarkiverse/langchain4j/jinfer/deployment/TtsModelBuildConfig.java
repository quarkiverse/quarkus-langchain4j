package io.quarkiverse.langchain4j.jinfer.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface TtsModelBuildConfig {

    /**
     * Whether the text-to-speech model should be enabled.
     */
    @ConfigDocDefault("true")
    Optional<Boolean> enabled();
}
