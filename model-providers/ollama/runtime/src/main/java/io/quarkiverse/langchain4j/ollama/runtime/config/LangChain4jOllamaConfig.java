package io.quarkiverse.langchain4j.ollama.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
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
        Optional<String> baseUrl();

        /**
         * Timeout for Ollama calls
         */
        @ConfigDocDefault("10s")
        @WithDefault("${quarkus.langchain4j.timeout}")
        Optional<Duration> timeout();

        /**
         * Whether the Ollama client should log requests
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-requests}")
        Optional<Boolean> logRequests();

        /**
         * Whether the Ollama client should log responses
         */
        @ConfigDocDefault("false")
        @WithDefault("${quarkus.langchain4j.log-responses}")
        Optional<Boolean> logResponses();

        /**
         * Whether to enable the integration. Defaults to {@code true}, which means requests are made to the OpenAI
         * provider.
         * Set to {@code false} to disable all requests.
         */
        @WithDefault("true")
        Boolean enableIntegration();

        /**
         * the experimental tools name. Currently, the only accepted values are {@code NONE, PARALLEL, SEQUENTIAL}
         * NONE: for no tools, works as before without handling tools.
         * PARALLEL: Tools we be used and simulated with one call to llm, that will answer with all tool request to execute
         * and the response using the result of the tool request.
         */
        @WithDefault("NONE")
        Optional<String> experimentalTools();

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
