package io.quarkiverse.langchain4j.audit;

/**
 * Invoked when the final result of the AiService method has been computed
 */
public record LLMInteractionCompleteEvent(AuditSourceInfo sourceInfo, Object result) implements LLMInteractionEvent {
}
