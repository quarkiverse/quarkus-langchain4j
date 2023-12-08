package io.quarkiverse.langchain4j.samples;

import jakarta.inject.Singleton;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

@Singleton
public class CustomChatMemoryProvider implements ChatMemoryProvider {

    private final ChatMemoryStore store;

    public CustomChatMemoryProvider() {
        this.store = createCustomStore();
    }

    private static ChatMemoryStore createCustomStore() {
        // TODO: provide some kind of custom store
        return null;
    }

    @Override
    public ChatMemory get(Object memoryId) {
        return createCustomMemory(memoryId);
    }

    private static ChatMemory createCustomMemory(Object memoryId) {
        // TODO: implement using memoryId and store
        return null;
    }
}
