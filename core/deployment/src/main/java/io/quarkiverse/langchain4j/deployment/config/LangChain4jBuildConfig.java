package io.quarkiverse.langchain4j.deployment.config;

import static io.quarkus.runtime.annotations.ConfigPhase.BUILD_TIME;

import java.util.Map;
import java.util.OptionalInt;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = BUILD_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j")
public interface LangChain4jBuildConfig {

    /**
     * Default model config.
     */
    @WithParentName
    @ConfigDocSection
    BaseConfig defaultConfig();

    /**
     * Named model config.
     */
    @WithParentName
    @ConfigDocMapKey("model-name")
    @ConfigDocSection
    Map<String, BaseConfig> namedConfig();

    /**
     * DevServices related configuration
     */
    DevServicesConfig devservices();

    /**
     * Mock model API (quarkus-langchain4j-mock-api) configuration
     */
    MockApiConfig mockApi();

    /**
     * Configuration property to enable or disable the use of the {response schema} placeholder in
     * the @SystemMessage/@UserMessage.
     */
    @WithDefault("true")
    boolean responseSchema();

    interface BaseConfig {
        /**
         * Chat model
         */
        ChatModelConfig chatModel();

        /**
         * Rerank model
         */
        ScoringModelConfig scoringModel();

        /**
         * Embedding model
         */
        EmbeddingModelConfig embeddingModel();

        /**
         * Moderation model
         */
        ModerationModelConfig moderationModel();

        /**
         * Image model
         */
        ImageModelConfig imageModel();

        /**
         * Audio transcription model
         */
        AudioTranscriptionModelConfig audioTranscriptionModel();

        /**
         * Decision model
         */
        DecisionModelConfig decisionModel();
    }

    @ConfigGroup
    interface DevServicesConfig {
        /**
         * If DevServices has been explicitly enabled or disabled. DevServices is generally enabled by default, unless there is
         * an
         * existing configuration present.
         * <p>
         * When DevServices is enabled Quarkus will attempt to automatically serve a model if there are any matching ones.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * The default port where the inference server listens for requests
         */
        @WithDefault("11434")
        Integer port();

        /**
         * Instructs Ollama to preload a model in order to get faster response times
         */
        @WithDefault("true")
        boolean preload();
    }

    @ConfigGroup
    interface MockApiConfig {
        /**
         * Whether the mock model API is enabled. Requires the quarkus-langchain4j-mock-api extension; when enabled, the
         * LangChain4j dev services are not started because every model is pointed at the mock.
         */
        @WithDefault("false")
        boolean enabled();

        /**
         * The port the mock server listens on. When not set, a random free port is used.
         */
        OptionalInt port();

        /**
         * The reply returned for chat requests when no stub matches.
         */
        @WithDefault("This is a mock reply from the Quarkus LangChain4j mock model API.")
        String defaultChatReply();

        /**
         * The dimension of the embeddings returned when no stub matches.
         */
        @WithDefault("4")
        int defaultEmbeddingDimension();
    }
}
