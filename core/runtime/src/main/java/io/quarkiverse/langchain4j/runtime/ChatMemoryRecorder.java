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
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ChatMemoryRecorder {
    private final RuntimeValue<ChatMemoryConfig> config;

    public ChatMemoryRecorder(RuntimeValue<ChatMemoryConfig> config) {
        this.config = config;
    }

    public Function<SyntheticCreationalContext<ChatMemoryProvider>, ChatMemoryProvider> messageWindow() {
        return new Function<>() {
            @Override
            public ChatMemoryProvider apply(SyntheticCreationalContext<ChatMemoryProvider> context) {
                ChatMemoryStore chatMemoryStore = context.getInjectedReference(ChatMemoryStore.class);
                int maxMessages = config.getValue().memoryWindow().maxMessages();
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

    public Function<SyntheticCreationalContext<ChatMemoryProvider>, ChatMemoryProvider> tokenWindow() {
        return new Function<>() {
            @Override
            public ChatMemoryProvider apply(SyntheticCreationalContext<ChatMemoryProvider> context) {
                ChatMemoryStore chatMemoryStore = context.getInjectedReference(ChatMemoryStore.class);
                TokenCountEstimator tokenizer = context.getInjectedReference(TokenCountEstimator.class);
                int maxTokens = config.getValue().tokenWindow().maxTokens();
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
