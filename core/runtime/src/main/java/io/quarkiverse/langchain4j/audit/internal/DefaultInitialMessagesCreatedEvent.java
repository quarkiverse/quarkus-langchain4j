package io.quarkiverse.langchain4j.audit.internal;

import java.util.Optional;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.InitialMessagesCreatedEvent;

public record DefaultInitialMessagesCreatedEvent(AuditSourceInfo sourceInfo, Optional<SystemMessage> systemMessage,
        UserMessage userMessage) implements InitialMessagesCreatedEvent {
}