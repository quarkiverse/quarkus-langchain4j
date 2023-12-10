package io.quarkiverse.langchain4j;

import java.util.List;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryRemovable;

/**
 * Allows the application to manually control when a {@link ChatMemory} should be removed from the underlying
 * {@link ChatMemoryStore}.
 */
public final class ChatMemoryRemover {

    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    private ChatMemoryRemover() {
    }

    /**
     * @param aiService The bean that implements the AI Service annotated with {@link RegisterAiService}
     * @param memoryId The object used as memory IDs for which the corresponding {@link ChatMemory} should be removed
     */
    public static void remove(Object aiService, Object memoryId) {
        if (aiService instanceof ChatMemoryRemovable r) {
            r.remove(memoryId);
        }
    }

    /**
     * @param aiService The bean that implements the AI Service annotated with {@link RegisterAiService}
     * @param memoryIds The objects used as memory IDs for which the corresponding {@link ChatMemory} should be removed
     */
    public static void remove(Object aiService, List<Object> memoryIds) {
        if (aiService instanceof ChatMemoryRemovable r) {
            r.remove(memoryIds.toArray(EMPTY_OBJECT_ARRAY));
        }
    }
}
