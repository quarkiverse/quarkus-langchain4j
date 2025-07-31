package io.quarkiverse.langchain4j.test;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

public interface ToolnameValidation {
    @RegisterAiService
    @ApplicationScoped
    public interface ChatToolOne {
        @SystemMessage("You are a helpful assistant.")
        @Tool("help chat")
        String chat(@UserMessage String message);
    }

    @RegisterAiService
    @ApplicationScoped
    public interface ChatToolTwo {

        @SystemMessage("You are a helpful assistant.")
        @Tool("help chat")
        String chat(@UserMessage String message, int number);
    }

    @RegisterAiService
    @ApplicationScoped
    public interface ChatterTools {

        @SystemMessage("You are a helpful assistant.")
        @Tool(name = "Chatter", value = "help chat")
        String chat(@UserMessage String message, int number);
    }

    @Singleton
    public static class MyLanguageModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            return null;
        }
    }

    @Singleton
    public static class MyChatMemoryProvider implements ChatMemoryProvider {
        @Override
        public ChatMemory get(Object memoryId) {
            return null;
        }
    }

}
