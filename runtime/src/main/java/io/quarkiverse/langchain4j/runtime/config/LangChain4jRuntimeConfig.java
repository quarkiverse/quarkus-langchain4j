package io.quarkiverse.langchain4j.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j")
public interface LangChain4jRuntimeConfig {

    /**
     * Chat model related settings
     */
    ChatModelRuntime chatModel();

    /**
     * Chat model related settings
     */
    ChatModelRuntime languageModel();

    /**
     * Embedding model related settings
     */
    EmbeddingModelRuntime embeddingModel();

    /**
     * Moderation model related settings
     */
    ModerationModelRuntime moderationModel();

    /**
     * Connection related settings for OpenAI
     */
    @WithName("openai")
    OpenAiServer openAi();

    /**
     * Connection related settings for running LLMs locally
     */
    @WithName("local")
    LocalAiChatParams localAi();

    /**
     * Connection related settings for running LLMs locally
     */
    HuggingFace huggingFace();
}
