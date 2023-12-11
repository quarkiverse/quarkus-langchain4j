package io.quarkiverse.langchain4j.openai.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ModerationModelConfig {

    /**
     * Moderation Model name to use. 
     *
     * See https://platform.openai.com/docs/guides/moderation/overview for a list of available models.
     */
    @WithDefault("text-moderation-latest")
    String modelName();
}
