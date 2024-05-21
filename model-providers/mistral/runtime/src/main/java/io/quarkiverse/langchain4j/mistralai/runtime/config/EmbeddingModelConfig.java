package io.quarkiverse.langchain4j.mistralai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Model name to use
     */
    @WithDefault("mistral-embed")
    String modelName();

    /**
     * Whether embedding model requests should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.mistralai.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether embedding model responses should be logged
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.mistralai.log-responses}")
    Optional<Boolean> logResponses();

}
