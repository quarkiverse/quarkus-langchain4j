package io.quarkiverse.langchain4j.runtime.config;

import java.util.Optional;

import io.quarkiverse.langchain4j.runtime.aiservice.ToolsExecutionMode;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithParentName;

/**
 * Per-AiService tool-execution override.
 * <p>
 * The {@link #mode()} is {@link Optional} so that "unset" preserves inheritance from the global
 * {@code quarkus.langchain4j.tools.execution} setting. Any value here takes precedence over the global default
 * for the named AiService.
 *
 * Example: {@code quarkus.langchain4j.myAiService.tools.execution} set to {@code virtual-threads}.
 */
@ConfigGroup
public interface AiServiceToolsExecutionConfig {

    /**
     * Per-service execution mode override. When unset, the global
     * {@code quarkus.langchain4j.tools.execution} mode is used.
     */
    @WithParentName
    Optional<ToolsExecutionMode> mode();
}
