package io.quarkiverse.langchain4j.audit;

/**
 * Invoked when the final result of the AiService method has been computed
 */
public interface LLMInteractionCompleteEvent extends LLMInteractionEvent {
    /**
     * @return the result of the AiService method
     */
    Object result();
}
