package io.quarkiverse.langchain4j.vertexai.runtime.gemini.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

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
@ConfigMapping(prefix = "quarkus.langchain4j.vertexai.gemini")
public interface LangChain4jVertexAiGeminiConfig {
    /**
     * Default model config
     */
    @WithParentName
    VertexAiGeminiConfig defaultConfig();

    /**
     * Named model config
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, VertexAiGeminiConfig> namedConfig();

    @ConfigGroup
    interface VertexAiGeminiConfig {

        /**
         * The unique identifier of the project
         */
        @WithDefault("dummy") // TODO: this should be optional but Smallrye Config doesn't like it
        String projectId();

        /**
         * GCP location
         */
        @WithDefault("dummy") // TODO: this should be optional but Smallrye Config doesn't like it
        String location();

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
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to the Vertex AI
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
         * Chat model related settings
         */
        ChatModelConfig chatModel();
    }

    /**
     * Whether to use the current security identity's access token to access Vertex AI provider.
     * If it is set to {@code true} but the security identity has no access token then default Google application credentials
     * which must be setup in your environment will be used.
     * Set to {@code false} to access Vertex AI provider only with the default Google application credentials.
     */
    @WithDefault("false")
    Boolean useSecurityIdentityToken();
}
