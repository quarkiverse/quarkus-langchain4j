package io.quarkiverse.langchain4j.audit;

/**
 * Invoked when the final result of the AiService method has been computed
 *
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public interface LLMInteractionCompleteEvent extends LLMInteractionEvent {
    /**
     * @return the result of the AiService method
     */
    Object result();
}
