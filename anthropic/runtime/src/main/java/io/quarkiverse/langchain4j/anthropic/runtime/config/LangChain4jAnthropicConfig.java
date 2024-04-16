package io.quarkiverse.langchain4j.anthropic.runtime.config;

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
@ConfigMapping(prefix = "quarkus.langchain4j.anthropic")
public interface LangChain4jAnthropicConfig {
    /**
     * Default model config
     */
    @WithParentName
    AnthropicConfig defaultConfig();

    /**
     * Named model config
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, AnthropicConfig> namedConfig();

    @ConfigGroup
    interface AnthropicConfig {
        /**
         * Base URL of the Anthropic API
         */
        @WithDefault("https://api.anthropic.com/v1/")
        String baseUrl();

        /**
         * Anthropic API key
         */
        @WithDefault("dummy") // TODO: this should be optional but Smallrye Config doesn't like it
        String apiKey();

        /**
         * The Anthropic version
         */
        @WithDefault("2023-06-01")
        String version();

        /**
         * Timeout for Anthropic calls
         */
        @WithDefault("10s")
        Duration timeout();

        /**
         * Whether the Anthropic client should log requests
         */
        @ConfigDocDefault("false")
        Optional<Boolean> logRequests();

        /**
         * Whether the Anthropic client should log responses
         */
        @ConfigDocDefault("false")
        Optional<Boolean> logResponses();

        /**
         * Whether or not to enable the integration. Defaults to {@code true}, which means requests are made to the Anthropic
         * provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * Chat model related settings
         */
        ChatModelConfig chatModel();
    }
}
