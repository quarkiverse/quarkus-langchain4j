package io.quarkiverse.langchain4j.audit;

/**
 * Invoked when there was an exception computing the result of the AiService method
 */
public record LLMInteractionFailureEvent(AuditSourceInfo sourceInfo, Exception error) implements LLMInteractionEvent {
}
