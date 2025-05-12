package io.quarkiverse.langchain4j.audit;

import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Invoked with a response from an LLM. It is important to note that this can be invoked multiple times
 * when tools exist.
 */
public record ResponseFromLLMReceivedEvent(AuditSourceInfo sourceInfo,
        ChatResponse response) implements LLMInteractionEvent {
}
