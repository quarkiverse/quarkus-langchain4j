package io.quarkiverse.langchain4j.typesafe.runtime.config;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface DecisionModelConfig {

    /**
     * Decision model to use (e.g. jev-latest, jev-1.13.0, devops-thiago/classone-gemma4-e2b).
     */
    @WithDefault("jev-latest")
    String modelName();

    /**
     * Timeout for TypeSafe calls.
     */
    @WithDefault("30s")
    Duration timeout();

    /**
     * Maximum number of retries.
     */
    @WithDefault("2")
    Integer maxRetries();

    /**
     * Whether request bodies should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether response bodies should be logged.
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * Custom headers to include in requests.
     */
    Map<String, String> customHeaders();
}
