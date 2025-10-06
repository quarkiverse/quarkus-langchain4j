package io.quarkiverse.langchain4j.audit;

/**
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public interface LLMInteractionEvent {
    AuditSourceInfo sourceInfo();
}
