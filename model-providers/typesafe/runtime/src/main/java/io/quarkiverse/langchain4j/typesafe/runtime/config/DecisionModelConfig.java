package io.quarkiverse.langchain4j.typesafe.runtime.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface DecisionModelConfig {

    /**
     * Decision model to use when a request does not specify one
     */
    @WithDefault("jev-latest")
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
