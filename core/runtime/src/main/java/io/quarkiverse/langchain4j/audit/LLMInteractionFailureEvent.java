package io.quarkiverse.langchain4j.audit;

/**
 * Invoked when there was an exception computing the result of the AiService method
 *
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public interface LLMInteractionFailureEvent extends LLMInteractionEvent {
    /**
     * @return the error that occurred
     */
    Exception error();
}
