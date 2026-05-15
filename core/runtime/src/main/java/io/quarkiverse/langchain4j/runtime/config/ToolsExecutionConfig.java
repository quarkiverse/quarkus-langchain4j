package io.quarkiverse.langchain4j.runtime.config;

import io.quarkiverse.langchain4j.runtime.aiservice.ToolsExecutionMode;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

/**
 * Configuration for tool-execution mode at the global ({@code quarkus.langchain4j.tools.*}) scope.
 * <p>
 * Controls whether multiple tool calls returned by the LLM in a single response are dispatched serially (default)
 * or concurrently using a Quarkus-managed executor.
 *
 * <pre>
 * # Global default applied to every AiService unless overridden per-service
 * quarkus.langchain4j.tools.execution=serial            # serial | virtual-threads | worker-pool
 *
 * # Bounded concurrency for the virtual-threads mode (global only)
 * quarkus.langchain4j.tools.execution.virtual-threads.max-concurrency=64
 *
 * # Per-AiService override (e.g. for an AiService whose declared name is "myAiService")
 * quarkus.langchain4j.myAiService.tools.execution=virtual-threads
 * </pre>
 */
@ConfigGroup
public interface ToolsExecutionConfig {

    int DEFAULT_VT_MAX_CONCURRENCY = 64;

    /**
     * The global execution mode for tool calls returned by the LLM.
     * <p>
     * <ul>
     * <li>{@code serial} (default): tool calls are executed one at a time, in the order returned by the LLM.</li>
     * <li>{@code virtual-threads}: tool calls are executed concurrently on a virtual-thread executor (Java 21+).
     * On Java 17-20 the extension falls back to {@code serial} and logs a warning at startup.</li>
     * <li>{@code worker-pool}: tool calls are executed concurrently on the Quarkus {@code ManagedExecutor}
     * (Vert.x worker pool, sized via {@code quarkus.thread-pool.*}).</li>
     * </ul>
     */
    @WithParentName
    @WithDefault("serial")
    ToolsExecutionMode mode();

    /**
     * Maximum concurrent tool executions when {@link #mode()} is {@code virtual-threads}.
     * <p>
     * Has no effect on {@code serial} or {@code worker-pool}. The {@code worker-pool} mode is bounded by
     * {@code quarkus.thread-pool.*} configuration instead.
     */
    @WithName("virtual-threads.max-concurrency")
    @WithDefault("" + DEFAULT_VT_MAX_CONCURRENCY)
    int virtualThreadsMaxConcurrency();
}
