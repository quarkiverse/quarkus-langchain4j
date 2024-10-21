package io.quarkiverse.langchain4j.jlama.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelFixedRuntimeConfig {

    /**
     * Model name to use
     */
    @WithDefault("intfloat/e5-small-v2")
    String modelName();

}
