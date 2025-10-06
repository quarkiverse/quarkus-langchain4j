package io.quarkiverse.langchain4j.audit;

import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Invoked with a response from an LLM. It is important to note that this can be invoked multiple times
 * when tools exist.
 *
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public interface ResponseFromLLMReceivedEvent extends LLMInteractionEvent {
    /**
     * @return the chat response
     */
    ChatResponse response();
}