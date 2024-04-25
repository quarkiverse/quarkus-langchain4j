package io.quarkiverse.langchain4j.watsonx.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.watsonx")
public interface LangChain4jWatsonxConfig {

    /**
     * Default model config.
     */
    @WithParentName
    WatsonConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, WatsonConfig> namedConfig();

    @ConfigGroup
    interface WatsonConfig {
        /**
         * Base URL
         */
        @WithDefault("https://dummy.ai/api")
        String baseUrl();

        /**
         * IBM Cloud API key
         */
        @WithDefault("dummy")
        String apiKey();

        /**
         * Timeout for watsonx.ai API calls
         */
        @WithDefault("10s")
        Duration timeout();

        /**
         * The version date for the API of the form YYYY-MM-DD
         */
        @WithDefault("2024-03-14")
        String version();

        /**
         * Watsonx.ai project id.
         */
        @WithDefault("dummy")
        String projectId();

        /**
         * Whether the watsonx.ai client should log requests
         */
        @WithDefault("false")
        Boolean logRequests();

        /**
         * Whether the watsonx.ai client should log responses
         */
        @WithDefault("false")
        Boolean logResponses();

        /**
         * Whether or not to enable the integration. Defaults to {@code true}, which means requests are made to the watsonx.ai
         * provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * Chat model related settings
         */
        IAMConfig iam();

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();

        /**
         * Embedding model related settings
         */
        EmbeddingModelConfig embeddingModel();
    }
}
