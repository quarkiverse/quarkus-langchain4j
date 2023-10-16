package io.quarkiverse.langchain4j.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface OpenAiServer {

    /**
     * Base URL of OpenAI API
     */
    @WithDefault("https://api.openai.com/v1/")
    String baseUrl();

    /**
     * OpenAI API key
     */
    Optional<String> apiKey();

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
}
