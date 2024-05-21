package io.quarkiverse.langchain4j.bam.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface EmbeddingModelConfig {

    /**
     * Model to use
     */
    @WithDefault("ibm/slate.125m.english.rtrvr")
    String modelId();

    /**
     * Whether the BAM embedding model should log requests
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.bam.log-requests}")
    Optional<Boolean> logRequests();

    /**
     * Whether the BAM embedding model should log requests
     */
    @ConfigDocDefault("false")
    @WithDefault("${quarkus.langchain4j.log-responses}")
    Optional<Boolean> logResponses();
}
