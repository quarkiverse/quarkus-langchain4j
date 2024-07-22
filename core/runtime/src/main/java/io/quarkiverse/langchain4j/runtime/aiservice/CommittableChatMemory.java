package io.quarkiverse.langchain4j.runtime.aiservice;

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
}
