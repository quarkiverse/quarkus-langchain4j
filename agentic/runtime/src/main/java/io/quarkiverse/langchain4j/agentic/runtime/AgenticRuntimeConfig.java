package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.agent")
public interface AgenticRuntimeConfig {

    /**
     * Default agent configuration applied to all agents unless overridden by a named config.
     */
    @WithParentName
    AgentConfig defaultConfig();

    /**
     * Per-agent configuration keyed by agent name.
     * Properties not set on a named agent inherit from the default config.
     */
    @WithParentName
    @WithDefaults
    @ConfigDocMapKey("agent-name")
    Map<String, AgentConfig> namedConfig();

    /** Dev UI configuration. */
    DevUiConfig devUi();

    interface AgentConfig {

        /**
         * Maximum iterations for loop and planner agents.
         * When absent, langchain4j-agentic uses its own default.
         */
        Optional<Integer> maxIterations();

        /**
         * Maximum number of sub-agent invocations within a supervisor workflow.
         * Declared but not wired in C6 — requires upstream supervisor builder SPI
         * or workflow-level AgentConfigurator.
         */
        Optional<Integer> maxAgentsInvocations();

        /**
         * The A2A server URL for remote agent communication.
         * When set, the agent connects to a remote A2A-compatible server at this URL.
         */
        Optional<String> a2aServerUrl();
    }

    interface DevUiConfig {

        /**
         * Whether to eagerly initialise root agents at startup in dev mode.
         * Set to {@code false} in CI environments to avoid unnecessary agent startup latency.
         */
        @WithDefault("true")
        boolean eagerInit();
    }
}
