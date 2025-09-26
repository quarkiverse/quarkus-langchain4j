package io.quarkiverse.langchain4j.ai.runtime.gemini.config;

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
@ConfigMapping(prefix = "quarkus.langchain4j.ai.gemini")
public interface LangChain4jAiGeminiConfig {
    /**
     * Default model config
     */
    @WithParentName
    AiGeminiConfig defaultConfig();

    /**
     * Named model config
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, AiGeminiConfig> namedConfig();

    @ConfigGroup
    interface AiGeminiConfig {

        /**
         * The api key
         */
        Optional<String> apiKey();

        /**
         * Publisher of model
         */
        @WithDefault("google")
        String publisher();

        /**
         * Meant to be used for testing only in order to override the base URL used by the client
         */
        Optional<String> baseUrl();

        /**
         * The API version to use for this operation.
         */
        @WithDefault("v1")
        String apiVersion();

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to the Vertex AI Gemini
         * provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * Whether the Vertex AI client should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether the Vertex AI client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();

        /**
         * Timeout for requests to gemini APIs
         */
        @WithDefault("${quarkus.langchain4j.timeout}")
        Optional<Duration> timeout();

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();

        /**
         * Embedding model related settings
         */
        LangChain4jAiGeminiEmbeddingConfig embeddingModel();
    }
}
