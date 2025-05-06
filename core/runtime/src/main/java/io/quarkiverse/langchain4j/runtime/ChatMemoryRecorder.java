package io.quarkiverse.langchain4j.runtime;

import java.util.function.Function;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ChatMemoryRecorder {

    public Function<SyntheticCreationalContext<ChatMemoryProvider>, ChatMemoryProvider> messageWindow(ChatMemoryConfig config) {
        return new Function<>() {
            @Override
            public ChatMemoryProvider apply(SyntheticCreationalContext<ChatMemoryProvider> context) {
                ChatMemoryStore chatMemoryStore = context.getInjectedReference(ChatMemoryStore.class);
                int maxMessages = config.memoryWindow().maxMessages();
                return new ChatMemoryProvider() {
                    @Override
                    public ChatMemory get(Object memoryId) {
                        return MessageWindowChatMemory.builder()
                                .maxMessages(maxMessages)
                                .id(memoryId)
                                .chatMemoryStore(chatMemoryStore)
                                .build();
                    }
                };
            }
        };
    }

    public Function<SyntheticCreationalContext<ChatMemoryProvider>, ChatMemoryProvider> tokenWindow(ChatMemoryConfig config) {
        return new Function<>() {
            @Override
            public ChatMemoryProvider apply(SyntheticCreationalContext<ChatMemoryProvider> context) {
                ChatMemoryStore chatMemoryStore = context.getInjectedReference(ChatMemoryStore.class);
                TokenCountEstimator tokenizer = context.getInjectedReference(TokenCountEstimator.class);
                int maxTokens = config.tokenWindow().maxTokens();
                return new ChatMemoryProvider() {
                    @Override
                    public ChatMemory get(Object memoryId) {
                        return TokenWindowChatMemory.builder()
                                .maxTokens(maxTokens, tokenizer)
                                .id(memoryId)
                                .chatMemoryStore(chatMemoryStore)
                                .build();
                    }
                };
            }
        };
    }
}
