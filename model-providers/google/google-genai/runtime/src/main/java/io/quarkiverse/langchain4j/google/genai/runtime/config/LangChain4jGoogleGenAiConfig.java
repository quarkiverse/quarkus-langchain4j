package io.quarkiverse.langchain4j.google.genai.runtime.config;

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
@ConfigMapping(prefix = "quarkus.langchain4j.google.genai")
public interface LangChain4jGoogleGenAiConfig {
    /**
     * Default model config
     */
    @WithParentName
    GoogleGenAiConfig defaultConfig();

    /**
     * Named model config
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, GoogleGenAiConfig> namedConfig();

    @ConfigGroup
    interface GoogleGenAiConfig {

        /**
         * The API key.
         * <p>
         * Required when using the Gemini Developer API backend (the default). When {@code vertex-ai} is set to
         * {@code true}, the API key is not used and authentication relies on Application Default Credentials (ADC).
         */
        Optional<String> apiKey();

        /**
         * Whether to use the Vertex AI backend instead of the Gemini Developer API.
         * <p>
         * When set to {@code true}, requests are sent to the Vertex AI endpoint and authentication uses Google
         * Application Default Credentials (ADC) instead of an API key. In that case {@code project} and {@code location}
         * must be configured (or provided through the standard {@code GOOGLE_CLOUD_PROJECT} /
         * {@code GOOGLE_CLOUD_LOCATION} environment variables).
         */
        @WithDefault("false")
        Boolean vertexAi();

        /**
         * The Google Cloud project id to use when {@code vertex-ai} is enabled.
         * <p>
         * If not set, the {@code GOOGLE_CLOUD_PROJECT} environment variable is used.
         */
        Optional<String> project();

        /**
         * The Google Cloud location (region) to use when {@code vertex-ai} is enabled, e.g. {@code us-central1}.
         * <p>
         * If not set, the {@code GOOGLE_CLOUD_LOCATION} environment variable is used.
         */
        Optional<String> location();

        /**
         * Meant to be used for testing only in order to override the base URL used by the client
         */
        Optional<String> baseUrl();

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to the Google GenAI
         * provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * Whether the client should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether the client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();

        /**
         * Timeout for requests to Google GenAI APIs
         */
        @ConfigDocDefault("10s")
        @WithDefault("${quarkus.langchain4j.timeout}")
        Optional<Duration> timeout();

        /**
         * The name of the Quarkus Proxy Registry configuration to use.
         * <p>
         * If not set and the default proxy configuration is configured ({@code quarkus.proxy.*}) then that will be used.
         * If a name is configured, it uses the configuration from {@code quarkus.proxy.<name>.*}.
         */
        Optional<String> proxyConfigurationName();

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
