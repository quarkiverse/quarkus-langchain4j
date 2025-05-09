package io.quarkiverse.langchain4j.audit.internal;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.ToolExecutedEvent;

/**
 * Default implementation of {@link ToolExecutedEvent}.
 */
public record DefaultToolExecutedEvent(AuditSourceInfo sourceInfo, ToolExecutionRequest request,
        String result) implements ToolExecutedEvent {
}