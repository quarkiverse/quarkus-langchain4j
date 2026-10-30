package io.quarkiverse.langchain4j.openai.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface DecisionModelConfig {

    /**
     * Model name to use
     */
    @WithDefault("gpt-6-luna")
    String modelName();

    /**
     * Whether decision model requests should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether decision model responses should be logged
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();
}
