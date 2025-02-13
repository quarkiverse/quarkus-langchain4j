package io.quarkiverse.langchain4j.audit;

public interface LLMInteractionEvent {
    AuditSourceInfo sourceInfo();
}
