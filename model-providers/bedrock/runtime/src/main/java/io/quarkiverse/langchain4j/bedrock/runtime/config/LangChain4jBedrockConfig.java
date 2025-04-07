package io.quarkiverse.langchain4j.bedrock.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.bedrock")
public interface LangChain4jBedrockConfig {

    /**
     * Default model config.
     */
    @WithParentName
    BedrockConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, BedrockConfig> namedConfig();

    interface BedrockConfig {

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to Bedrock.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        boolean enableIntegration();

        /**
         * Whether the Bedrock client should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether the Bedrock client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();

        /**
         * Whether chat model body in request and response should be logged
         */
        @WithDefault("false")
        Optional<Boolean> logBody();

        /**
         * Aws sdk related configurations
         */
        AwsConfig aws();

        /**
         * Http client related configurations
         */
        HttpClientConfig client();

        /**
         * Chat model related configurations
         */
        ChatModelConfig chatModel();

        /**
         * Embedding model related configurations
         */
        EmbeddingModelConfig embeddingModel();
    }
}
