package io.quarkiverse.langchain4j.vertexai.runtime.models.config;

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
@ConfigMapping(prefix = "quarkus.langchain4j.vertexai")
public interface LangChain4jVertexAiConfig {
    /**
     * Default model config
     */
    @WithParentName
    VertexAiConfig defaultConfig();

    /**
     * Named model config
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, VertexAiConfig> namedConfig();

    @ConfigGroup
    interface VertexAiConfig {

        /**
         * The unique identifier of the Google Cloud project
         */
        @WithDefault("dummy") // TODO: To be improved and find a way to avoid to define default value
        String projectId();

        /**
         * The Google Cloud location: europe-west1, us-east1, etc
         */
        @WithDefault("dummy") // TODO: To be improved and find a way to avoid to define default value
        String location();

        /**
         * The id of the model to use.
         *
         * @see <a href=
         *      "https://cloud.google.com/model-garden">https://cloud.google.com/model-garden</a>
         */
        @WithDefault("claude-opus-4-6")
        String modelId();

        /**
         * The publisher of the model to be used part of the model gardens:
         * <a href="https://cloud.google.com/model-garden?hl=en">...</a>
         */
        @WithDefault("anthropic")
        String publisher();

        /**
         * Meant to be used for testing only in order to override the base URL used by the client
         */
        Optional<String> baseUrl();

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to the Vertex AI Gemini
         * provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * The Proxy type
         */
        @WithDefault("HTTP")
        String proxyType();

        /**
         * The Proxy host
         */
        Optional<String> proxyHost();

        /**
         * The Proxy port
         */
        @WithDefault("3128")
        Integer proxyPort();

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
         * Timeout for HTTP requests issued to the Vertex AI API
         */
        @WithDefault("${quarkus.langchain4j.timeout}")
        Optional<Duration> timeout();

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();
    }
}
