package io.quarkiverse.langchain4j.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Container for {@code quarkus.langchain4j.tools.*} (and the per-service {@code quarkus.langchain4j.<svc>.tools.*}).
 * <p>
 * Exists so that {@link ToolsExecutionConfig} can hang off both the global root and the per-AiService named map.
 * <p>
 * See {@link ToolsExecutionConfig} for the available properties.
 */
@ConfigGroup
public interface ToolsConfig {

    int DEFAULT_VT_MAX_CONCURRENCY = 64;

    /**
     * Tool-execution settings.
     */
    ToolsExecutionConfig execution();

    /**
     * Maximum concurrent tool executions when {@link ToolsExecutionConfig#mode()} is {@code virtual-threads}.
     * <p>
     * Has no effect on {@code serial} or {@code worker-pool}. The {@code worker-pool} mode is bounded by
     * {@code quarkus.thread-pool.*} configuration instead.
     * <p>
     * Lives as a sibling of {@code execution} (not nested under it) because {@code tools.execution} is bound to
     * the mode value via {@link io.smallrye.config.WithParentName} — a YAML key can't be both a scalar and a
     * parent map, so the cap can't be nested under {@code tools.execution.virtual-threads.*}.
     */
    @WithName("virtual-threads.max-concurrency")
    @WithDefault("" + DEFAULT_VT_MAX_CONCURRENCY)
    int virtualThreadsMaxConcurrency();
}
