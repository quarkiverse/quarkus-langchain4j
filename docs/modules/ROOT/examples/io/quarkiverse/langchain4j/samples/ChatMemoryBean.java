package io.quarkiverse.langchain4j.samples;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Singleton;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import io.quarkiverse.langchain4j.RemovableChatMemoryProvider;

@Singleton
public class ChatMemoryBean implements RemovableChatMemoryProvider {

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
}
