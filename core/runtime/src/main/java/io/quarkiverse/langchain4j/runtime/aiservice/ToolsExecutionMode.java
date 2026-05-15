package io.quarkiverse.langchain4j.runtime.aiservice;

/**
 * Selects how tool calls returned by the LLM are dispatched when more than one tool call is requested in a single response.
 * <p>
 * Configured via {@code quarkus.langchain4j.tools.execution} (and the per-AiService override
 * {@code quarkus.langchain4j.<service-name>.tools.execution}).
 * <p>
 * The default is {@link #SERIAL}; the existing serial dispatch path is unchanged unless the user explicitly opts in.
 */
public enum ToolsExecutionMode {

    /**
     * Tool calls are executed sequentially, in the order returned by the LLM.
     * This is the default and matches behaviour prior to the parallel-tool-execution feature.
     */
    SERIAL,

    /**
     * Tool calls are executed concurrently on a virtual-thread executor.
     * Requires Java 21+ at runtime; on earlier JVMs the extension falls back to {@link #SERIAL} with a startup warning.
     * Concurrency is bounded by {@code quarkus.langchain4j.tools.execution.virtual-threads.max-concurrency}.
     */
    VIRTUAL_THREADS,

    /**
     * Tool calls are executed concurrently on the Quarkus {@code ManagedExecutor} (the worker pool).
     * Bounded by the existing {@code quarkus.thread-pool.*} configuration.
     */
    WORKER_POOL
}
