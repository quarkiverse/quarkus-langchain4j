package io.quarkiverse.langchain4j.runtime.config;

import io.quarkus.runtime.annotations.ConfigGroup;

/**
 * Container for {@code quarkus.langchain4j.tools.*} (and the per-service {@code quarkus.langchain4j.<svc>.tools.*}).
 * <p>
 * Exists so that {@link ToolsExecutionConfig} can hang off both the global root and the per-AiService named map.
 * <p>
 * See {@link ToolsExecutionConfig} for the available properties.
 */
@ConfigGroup
public interface ToolsConfig {

    /**
     * Tool-execution settings.
     */
    ToolsExecutionConfig execution();
}
