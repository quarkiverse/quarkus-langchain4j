package io.quarkiverse.langchain4j.ollama.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface DecisionModelConfig {

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
