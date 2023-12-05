package io.quarkiverse.langchain4j;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;

/**
 * Extends {@link ChatMemoryProvider} to allow for removing {@link ChatMemory}
 * when it is no longer needed.
 */
public interface RemovableChatMemoryProvider extends ChatMemoryProvider {

    void remove(Object id);
}
