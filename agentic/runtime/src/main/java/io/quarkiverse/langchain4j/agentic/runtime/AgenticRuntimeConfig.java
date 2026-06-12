package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
@ConfigMapping(prefix = "quarkus.langchain4j.agent")
public interface AgenticRuntimeConfig {

    /**
     * Maximum iterations for loop and planner agents.
     * When absent, langchain4j-agentic uses its own default.
     */
    Optional<Integer> defaultMaxIterations();

    /** Dev UI configuration. */
    DevUiConfig devUi();

    interface DevUiConfig {
        /**
         * Whether to eagerly initialise root agents at startup in dev mode.
         * Set to {@code false} in CI environments to avoid unnecessary agent startup latency.
         */
        @WithDefault("true")
        boolean eagerInit();
    }
}
