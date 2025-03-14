package io.quarkiverse.langchain4j.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigRoot(phase = RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j")
public interface LangChain4jConfig {

    /**
     * Whether clients should log requests
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequests();

    /**
     * Whether clients should log responses
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logResponses();

    /**
     * Global timeout for requests to LLM APIs
     */
    @ConfigDocDefault("10s")
    Optional<Duration> timeout();

    /**
     * Global temperature for LLM APIs
     */
    OptionalDouble temperature();

    /**
     * Guardrails configuration
     */
    GuardrailsConfig guardrails();

    /**
     * Tracing related configuration
     */
    TracingConfig tracing();
}
