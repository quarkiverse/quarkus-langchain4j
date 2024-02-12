package io.quarkiverse.langchain4j.watsonx.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.net.URL;
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
public interface Langchain4jWatsonConfig {

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
        @WithDefault("https://dummy.ai/api") // TODO: this is set to a dummy value because otherwise Smallrye Config cannot give a proper error for named models
        URL baseUrl();

        /**
         * Watsonx API key
         */
        @WithDefault("dummy")
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
        @WithDefault("dummy") // TODO: this is set to a dummy value because otherwise Smallrye Config cannot give a proper error for named models
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
         * Whether or not to enable the integration. Defaults to {@code true}, which means requests are made to the OpenAI
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
    }
}
