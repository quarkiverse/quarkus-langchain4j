package io.quarkiverse.langchain4j.watsonx.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.net.URL;
import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.watsonx")
public interface Langchain4jWatsonConfig {

    /**
     * Base URL
     */
    URL baseUrl();

    /**
     * Watsonx API key
     */
    String apiKey();

    /**
     * Timeout for Watsonx API calls
     */
    @WithDefault("10s")
    Duration timeout();

    /**
     * Version to use
     */
    @WithDefault("2023-05-29")
    String version();

    /**
     * Watsonx project id.
     */
    String projectId();

    /**
     * Whether the Watsonx client should log requests
     */
    @WithDefault("false")
    Boolean logRequests();

    /**
     * Whether the Watsonx client should log responses
     */
    @WithDefault("false")
    Boolean logResponses();

    /**
     * Chat model related settings
     */
    IAMConfig iam();

    /**
     * Chat model related settings
     */
    ChatModelConfig chatModel();
}
