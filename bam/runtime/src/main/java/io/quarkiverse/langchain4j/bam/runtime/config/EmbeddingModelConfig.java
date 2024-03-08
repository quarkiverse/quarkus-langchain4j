package io.quarkiverse.langchain4j.bam.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Model to use
     */
    @WithDefault("ibm/slate.125m.english.rtrvr")
    String modelId();
}
