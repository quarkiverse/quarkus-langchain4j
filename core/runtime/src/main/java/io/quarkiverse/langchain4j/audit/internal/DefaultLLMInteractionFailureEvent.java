package io.quarkiverse.langchain4j.audit.internal;

import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.LLMInteractionFailureEvent;

/**
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public record DefaultLLMInteractionFailureEvent(AuditSourceInfo sourceInfo,
        Exception error) implements LLMInteractionFailureEvent {
}