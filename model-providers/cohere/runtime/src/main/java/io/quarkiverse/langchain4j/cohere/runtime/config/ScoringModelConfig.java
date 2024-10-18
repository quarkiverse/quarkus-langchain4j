package io.quarkiverse.langchain4j.cohere.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface ScoringModelConfig {

    /**
     * Reranking model to use. The current list of supported models can be found in the
     * <a href="https://docs.cohere.com/docs/models">Cohere docs</a>
     */
    @WithDefault("rerank-multilingual-v2.0")
    String modelName();

    /**
     * Timeout for Cohere calls
     */
    @WithDefault("30s")
    Duration timeout();

    /**
     * Whether embedding model requests should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether embedding model responses should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * The maximum number of times to retry. 1 means exactly one attempt, with retrying disabled.
     *
     * @deprecated Using the fault tolerance mechanisms built in Langchain4j is not recommended. If possible, use MicroProfile
     *             Fault
     *             Tolerance instead.
     */
    @WithDefault("1")
    Integer maxRetries();
}
