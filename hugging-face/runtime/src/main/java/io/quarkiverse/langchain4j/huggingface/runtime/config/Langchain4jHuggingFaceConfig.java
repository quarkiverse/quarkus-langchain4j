package io.quarkiverse.langchain4j.huggingface.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.huggingface")
public interface Langchain4jHuggingFaceConfig {

    String DEFAULT_BASE_URL = "https://api-inference.huggingface.co";

    /**
     * Base URL of Hugging Face API
     *
     */
    @ConfigDocDefault(DEFAULT_BASE_URL)
    Optional<String> baseUrl();

    /**
     * HuggingFace API key
     */
    Optional<String> apiKey();

    /**
     * Timeout for HuggingFace calls
     */
    @WithDefault("10s")
    Duration timeout();

    /**
     * Chat model related settings
     */
    ChatModelConfig chatModel();

    /**
     * Embedding model related settings
     */
    EmbeddingModelConfig embeddingModel();
}
