package io.quarkiverse.langchain4j.localai.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.localai")
public interface Langchain4jLocalAiConfig {

    /**
     * Base URL of OpenAI API
     */
    String baseUrl();

    /**
     * Timeout for OpenAI calls
     */
    @WithDefault("10s")
    Duration timeout();

    /**
     * The maximum number of times to retry
     */
    @WithDefault("3")
    Integer maxRetries();

    /**
     * Whether the OpenAI client should log requests
     */
    @WithDefault("false")
    Boolean logRequests();

    /**
     * Whether the OpenAI client should log responses
     */
    @WithDefault("false")
    Boolean logResponses();

    /**
     * Chat model related settings
     */
    ChatModelConfig chatModel();

    /**
     * Embedding model related settings
     */
    EmbeddingModelConfig embeddingModel();

    /**
     * Moderation model related settings
     */
    ModerationModelConfig moderationModel();
}
