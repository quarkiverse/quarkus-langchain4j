package io.quarkiverse.langchain4j.audit.internal;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.ToolExecutedEvent;

/**
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public record DefaultToolExecutedEvent(AuditSourceInfo sourceInfo, ToolExecutionRequest request,
        String result) implements ToolExecutedEvent {
}