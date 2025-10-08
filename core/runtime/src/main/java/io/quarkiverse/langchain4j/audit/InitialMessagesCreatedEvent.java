package io.quarkiverse.langchain4j.audit;

import java.util.Optional;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Invoked when the original user and system messages have been created
 *
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public interface InitialMessagesCreatedEvent extends LLMInteractionEvent {

    /**
     * @return the optional system message
     */
    Optional<SystemMessage> systemMessage();

    /**
     * @return the user message
     */
    UserMessage userMessage();
}