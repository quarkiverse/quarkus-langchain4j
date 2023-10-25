package io.quarkiverse.langchain4j.openai.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ModerationModelConfig {

    /**
     * Model name to use
     */
    @WithDefault("text-moderation-latest")
    String modelName();
}
