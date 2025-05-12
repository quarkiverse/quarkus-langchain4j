package io.quarkiverse.langchain4j.audit;

import java.util.Optional;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Invoked when the original user and system messages have been created
 */
public record InitialMessagesCreatedEvent(AuditSourceInfo sourceInfo, Optional<SystemMessage> systemMessage,
        UserMessage userMessage) implements LLMInteractionEvent {
}
