package io.quarkiverse.langchain4j.cohere.runtime.config;

import java.time.Duration;

import io.smallrye.config.WithDefault;

public interface CohereScoringModelConfig {

    /**
     * Reranking model to use. The current list of supported models
     * can be found in the <a href="https://docs.cohere.com/docs/models">Cohere docs</a>
     */
    @WithDefault("rerank-multilingual-v2.0")
    String modelName();

    /**
     * Timeout for Cohere calls
     */
    @WithDefault("30s")
    Duration timeout();

    /**
     * Maximum number of retries for Cohere API invocations.
     */
    @WithDefault("3")
    Integer maxRetries();

}
