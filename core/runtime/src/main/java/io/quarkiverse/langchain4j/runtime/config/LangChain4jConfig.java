package io.quarkiverse.langchain4j.runtime.config;

import static io.quarkus.runtime.annotations.ConfigPhase.RUN_TIME;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigDocSection;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

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
     * Whether clients should log requests as cURL commands
     */
    @ConfigDocDefault("false")
    Optional<Boolean> logRequestsCurl();

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

    /**
     * AI Service configuration
     */
    AiServiceConfig aiService();

    /**
     * Global tool-execution configuration.
     * <p>
     * Set {@code quarkus.langchain4j.tools.execution} to {@code serial} (the default), {@code virtual-threads},
     * or {@code worker-pool}. See {@link ToolsExecutionConfig} for details and for the per-AiService override
     * (which is keyed by AiService name and accessed via {@link #namedAiServices()}).
     */
    ToolsConfig tools();

    /**
     * Per-AiService configuration overrides, keyed by the AiService's declared name (typically the simple class name
     * or the {@link io.quarkiverse.langchain4j.RegisterAiService} value).
     * <p>
     * Currently only carries {@code tools.execution} overrides; structured this way to leave room for additional
     * per-service runtime knobs without introducing more config roots.
     */
    @WithParentName
    @ConfigDocMapKey("ai-service-name")
    @ConfigDocSection
    Map<String, NamedAiServiceConfig> namedAiServices();

    /**
     * Per-AiService configuration leaf.
     */
    interface NamedAiServiceConfig {
        /**
         * Per-AiService {@code tools} overrides.
         */
        AiServiceToolsConfig tools();
    }
}
