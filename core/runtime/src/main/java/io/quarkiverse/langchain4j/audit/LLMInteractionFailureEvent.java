package io.quarkiverse.langchain4j.audit;

/**
 * Invoked when there was an exception computing the result of the AiService method
 */
public interface LLMInteractionFailureEvent extends LLMInteractionEvent {
    /**
     * @return the error that occurred
     */
    Exception error();
}
