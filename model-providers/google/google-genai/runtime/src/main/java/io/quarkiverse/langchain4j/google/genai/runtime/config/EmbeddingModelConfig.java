package io.quarkiverse.langchain4j.google.genai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

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
     * Optional task type for which the embeddings will be used.
     * <p>
     * Possible values: RETRIEVAL_QUERY, RETRIEVAL_DOCUMENT, SEMANTIC_SIMILARITY, CLASSIFICATION,
     * CLUSTERING, QUESTION_ANSWERING, FACT_VERIFICATION, CODE_RETRIEVAL_QUERY
     */
    Optional<String> taskType();

    /**
     * Whether embedding model requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether embedding model responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();
}
