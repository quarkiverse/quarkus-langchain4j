package io.quarkiverse.langchain4j.runtime;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithName;

@ConfigGroup
public interface ChatModelRuntime {

    /**
     * Settings for OpenAI
     */
    @WithName("openai")
    OpenAi openAi();

    /**
     * Settings for locally running LLMs
     */
    @WithName("local")
    LocalAi localAi();

    /**
     * Settings for Hugging Face
     */
    HuggingFace huggingFace();
}
