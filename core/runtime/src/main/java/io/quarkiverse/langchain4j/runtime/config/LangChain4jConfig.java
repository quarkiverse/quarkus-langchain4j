package io.quarkiverse.langchain4j.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Optional;

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
     * Guardrails configuration
     */
    GuardrailsConfig guardrails();
}
