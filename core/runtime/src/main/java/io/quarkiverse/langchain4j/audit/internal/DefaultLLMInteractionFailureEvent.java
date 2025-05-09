package io.quarkiverse.langchain4j.audit.internal;

import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.LLMInteractionFailureEvent;

/**
 * Default implementation of {@link LLMInteractionFailureEvent}
 */
public record DefaultLLMInteractionFailureEvent(AuditSourceInfo sourceInfo,
        Exception error) implements LLMInteractionFailureEvent {
}