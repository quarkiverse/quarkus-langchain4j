package io.quarkiverse.langchain4j.ai.runtime.gemini.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface LangChain4jAiGeminiEmbeddingConfig {

    /**
     * The id of the model to use.
     *
     * @see <a href=
     *      "https://ai.google.dev/gemini-api/docs/models/gemini">https://ai.google.dev/gemini-api/docs/models/gemini</a>
     */
    @WithDefault("text-embedding-004")
    String modelId();

    /**
     * Reduced dimension for the output embedding
     */
    Optional<Integer> outputDimension();

    /**
     * Whether chat model requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether chat model responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * Global timeout for requests to gemini APIs
     */
    @ConfigDocDefault("10s")
    @WithDefault("${quarkus.langchain4j.vertexai.gemini.timeout}")
    Optional<Duration> timeout();

}
