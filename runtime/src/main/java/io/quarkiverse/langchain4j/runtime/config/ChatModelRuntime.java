package io.quarkiverse.langchain4j.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithName;

@ConfigGroup
public interface ChatModelRuntime {

    /**
     * Settings for OpenAI
     */
    @WithName("openai")
    OpenAiChatParams openAi();
}
