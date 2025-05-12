package io.quarkiverse.langchain4j.audit.internal;

import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.LLMInteractionCompleteEvent;

public record DefaultLLMInteractionCompleteEvent(AuditSourceInfo sourceInfo,
        Object result) implements LLMInteractionCompleteEvent {
}