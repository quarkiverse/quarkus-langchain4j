package io.quarkiverse.langchain4j.openai.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.openai")
public interface LangChain4jOpenAiConfig {

    /**
     * Default model config.
     */
    @WithParentName
    OpenAiConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, OpenAiConfig> namedConfig();

    @ConfigGroup
    interface OpenAiConfig {

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
         * OpenAI Organization ID (https://platform.openai.com/docs/api-reference/organization-optional)
         */
        Optional<String> organizationId();

        /**
         * Timeout for OpenAI calls
         */
        @ConfigDocDefault("10s")
        @WithDefault("${quarkus.langchain4j.timeout}")
        Optional<Duration> timeout();

        /**
         * The maximum number of times to retry. 1 means exactly one attempt, with retrying disabled.
         *
         * @deprecated Using the built-in fault tolerance mechanisms is not recommended. If possible,
         *             use MicroProfile Fault Tolerance instead.
         */
        @WithDefault("1")
        Integer maxRetries();

        /**
         * Whether the OpenAI client should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether the OpenAI client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to the OpenAI
         * provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

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

        /**
         * Image model related settings
         */
        ImageModelConfig imageModel();
    }
}
