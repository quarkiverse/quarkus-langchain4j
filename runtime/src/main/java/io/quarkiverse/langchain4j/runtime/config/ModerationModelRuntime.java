package io.quarkiverse.langchain4j.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithName;

@ConfigGroup
public interface ModerationModelRuntime {

    /**
     * Settings for OpenAI
     */
    @WithName("openai")
    OpenAiModerationParams openAi();
}
