package io.quarkiverse.langchain4j.mistralai.runtime.config;

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
@ConfigMapping(prefix = "quarkus.langchain4j.mistralai")
public interface LangChain4jMistralAiConfig {

    /**
     * Default model config.
     */
    @WithParentName
    MistralAiConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, MistralAiConfig> namedConfig();

    @ConfigGroup
    interface MistralAiConfig {
        /**
         * Base URL of Mistral API
         */
        @WithDefault("https://api.mistral.ai/v1/")
        String baseUrl();

        /**
         * Mistral API key
         */
        @WithDefault("dummy") // TODO: this should be optional but Smallrye Config doesn't like it
        String apiKey();

        /**
         * Timeout for Mistral calls
         */
        @ConfigDocDefault("10s")
        @WithDefault("${quarkus.langchain4j.timeout}")
        Optional<Duration> timeout();

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
         * Whether the Mistral client should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether the Mistral client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to the Mistral AI
         * provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();
    }
}
