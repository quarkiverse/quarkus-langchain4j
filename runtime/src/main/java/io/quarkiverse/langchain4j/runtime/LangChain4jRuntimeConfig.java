package io.quarkiverse.langchain4j.runtime;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j")
public interface LangChain4jRuntimeConfig {

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
