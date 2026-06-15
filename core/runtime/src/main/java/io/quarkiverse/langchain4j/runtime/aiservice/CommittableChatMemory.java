package io.quarkiverse.langchain4j.runtime.aiservice;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;

/**
 * This is needed in order to make it possible defer updating {@link ChatMemory} until the implementation has completed
 * successfully.
 */
interface CommittableChatMemory extends ChatMemory {

    /**
     * Should be called to actually update the chat memory
     */
    void commit();

    /**
     * Replaces the last {@link AiMessage} in the buffered messages.
     * Used when output guardrails rewrite the AI response (e.g. after a reprompt).
     */
    void replaceLastAiMessage(AiMessage newAiMessage);
}
