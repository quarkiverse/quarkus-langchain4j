package io.quarkiverse.langchain4j.google.genai.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ChatModelBuildConfig {

    /**
     * Whether the chat model provider is enabled
     */
    @ConfigDocDefault("true")
    @WithDefault("true")
    Optional<Boolean> enabled();
}
