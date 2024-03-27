package io.quarkiverse.langchain4j.openai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Embedding Model name to use
     * 
     * See https://platform.openai.com/docs/guides/embeddings/embedding-models for a list of available models.
     */
    @WithDefault("text-embedding-ada-002")
    String modelName();

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

    /**
     * A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
     */
    Optional<String> user();
}
