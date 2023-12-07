package io.quarkiverse.langchain4j.samples;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;

public class ChatMemoryStoreProducer {

    @ApplicationScoped
    ChatMemoryStore produceChatMemoryStore() {
        return new InMemoryChatMemoryStore();
    }
}
