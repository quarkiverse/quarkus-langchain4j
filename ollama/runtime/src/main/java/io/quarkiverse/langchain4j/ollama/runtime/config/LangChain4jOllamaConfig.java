package io.quarkiverse.langchain4j.ollama.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Map;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.ollama")
public interface LangChain4jOllamaConfig {

    /**
     * Default model config.
     */
    @WithParentName
    OllamaConfig defaultConfig();

    /**
     * Named model config.
     */
    @ConfigDocSection
    @ConfigDocMapKey("model-name")
    @WithParentName
    @WithDefaults
    Map<String, OllamaConfig> namedConfig();

    interface OllamaConfig {
        /**
         * Base URL where the Ollama serving is running
         */
        @WithDefault("http://localhost:11434")
        String baseUrl();

        /**
         * Timeout for Ollama calls
         */
        @WithDefault("10s")
        Duration timeout();

        /**
         * Whether the Ollama client should log requests
         */
        @WithDefault("false")
        Boolean logRequests();

        /**
         * Whether the Ollama client should log responses
         */
        @WithDefault("false")
        Boolean logResponses();

        /**
         * Whether or not to enable the integration. Defaults to {@code true}, which means requests are made to the OpenAI
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
    }
}
