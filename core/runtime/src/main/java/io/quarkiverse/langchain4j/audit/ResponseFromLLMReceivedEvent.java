package io.quarkiverse.langchain4j.audit;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;

/**
 * Invoked with a response from an LLM. It is important to note that this can be invoked multiple times
 * when tools exist.
 */
public record ResponseFromLLMReceivedEvent(AuditSourceInfo sourceInfo,
        Response<AiMessage> response) implements LLMInteractionEvent {
}
