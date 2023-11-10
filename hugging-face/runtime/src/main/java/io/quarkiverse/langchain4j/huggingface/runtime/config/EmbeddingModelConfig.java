package io.quarkiverse.langchain4j.huggingface.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Model to use
     */
    @WithDefault("sentence-transformers/all-MiniLM-L6-v2")
    String modelId();

    /**
     * If the model is not ready, wait for it instead of receiving 503. It limits the number of requests required to get your
     * inference done. It is advised to only set this flag to true after receiving a 503 error as it will limit hanging in your
     * application to known places
     */
    @WithDefault("true")
    Boolean waitForModel();
}
