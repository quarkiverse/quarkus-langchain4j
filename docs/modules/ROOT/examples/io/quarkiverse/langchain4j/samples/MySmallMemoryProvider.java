package io.quarkiverse.langchain4j.samples;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import io.quarkiverse.langchain4j.RemovableChatMemoryProvider;

public class MySmallMemoryProvider implements Supplier<ChatMemoryProvider> {
    @Override
    public ChatMemoryProvider get() {
        return new RemovableChatMemoryProvider() {
            private final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

            @Override
            public ChatMemory get(Object memoryId) {
                return memories.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.builder()
                        .maxMessages(20)
                        .id(memoryId)
                        .build());
            }

            @Override
            public void remove(Object id) {
                memories.remove(id);
            }
        };
    }
}
