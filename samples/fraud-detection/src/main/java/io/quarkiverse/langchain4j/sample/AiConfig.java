package io.quarkiverse.langchain4j.sample;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

public class AiConfig {

    @ApplicationScoped
    public static class MemoryProvider implements Supplier<ChatMemory> {

        @Override
        public ChatMemory get() {
            return MessageWindowChatMemory.withMaxMessages(20);
        }
    }

}
