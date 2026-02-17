package io.quarkiverse.langchain4j.watsonx.runtime.config;

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

    /**
     * Configuration for built-in services.
     */
    BuiltinServiceConfig builtInService();

    @ConfigGroup
    interface WatsonConfig {

        /**
         * Specifies the base URL of the watsonx.ai API.
         * <p>
         * A list of all available URLs is provided in the IBM Watsonx.ai documentation at the
         * <a href="https://cloud.ibm.com/apidocs/watsonx-ai#endpoint-url">this link</a>.
         */
        Optional<String> baseUrl();

        /**
         * IBM Cloud API key.
         */
        Optional<String> apiKey();

        /**
         * Timeout for watsonx.ai calls.
         */
        @ConfigDocDefault("10s")
        @WithDefault("${quarkus.langchain4j.timeout}")
        Optional<Duration> timeout();

        /**
         * The version date for the API of the form YYYY-MM-DD.
         */
        @WithDefault("2025-04-23")
        String version();

        /**
         * The space that contains the resource.
         * <p>
         * Either <code>space_id</code> or <code>project_id</code> has to be given.
         */
        Optional<String> spaceId();

        /**
         * The project that contains the resource.
         * <p>
         * Either <code>space_id</code> or <code>project_id</code> has to be given.
         */
        Optional<String> projectId();

        /**
         * Whether the watsonx.ai client should log requests.
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether the watsonx.ai client should log responses.
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logResponses();

        /**
         * Whether the watsonx.ai client should log requests as cURL commands.
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests-curl}")
        Optional<Boolean> logRequestsCurl();

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to the watsonx.ai
         * provider. Set to {@code false} to
         * disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * IAM authentication related settings.
         */
        IAMConfig iam();

        /**
         * Cloud Object Storage related settings.
         * <p>
         * This configuration is only required when using the {@code TextExtraction} class.
         */
        Optional<TextExtractionConfig> textExtraction();

        /**
         * Chat model related settings.
         */
        ChatModelConfig chatModel();

        /**
         * Generation model related settings.
         */
        GenerationModelConfig generationModel();

        /**
         * Embedding model related settings.
         */
        EmbeddingModelConfig embeddingModel();

        /**
         * Scoring model related settings.
         */
        ScoringModelConfig scoringModel();
    }
}
