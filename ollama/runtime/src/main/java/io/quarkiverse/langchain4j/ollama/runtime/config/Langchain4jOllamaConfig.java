package io.quarkiverse.langchain4j.ollama.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.ollama")
public interface Langchain4jOllamaConfig {

    /**
     * Base URL where the Ollama serving is running
     */
    @WithDefault("http://localhost:11434")
    String baseUrl();

    /**
     * Timeout for HuggingFace calls
     */
    @WithDefault("10s")
    Duration timeout();

    /**
     * Whether the HuggingFace client should log requests
     */
    @WithDefault("false")
    Boolean logRequests();

    /**
     * Whether the HuggingFace client should log responses
     */
    @WithDefault("false")
    Boolean logResponses();

    /**
     * Chat model related settings
     */
    ChatModelConfig chatModel();
}
