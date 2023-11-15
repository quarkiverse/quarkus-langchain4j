package org.acme.example.openai.aiservices;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestQuery;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("assistant-with-tool")
public class AssistantWithToolsResource {

    private final Assistant assistant;

    public AssistantWithToolsResource(Assistant assistant) {
        this.assistant = assistant;
    }

    @GET
    public String get(@RestQuery String message) {
        return assistant.chat(message);
    }

    @RegisterAiService(tools = Calculator.class, chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class)
    public interface Assistant {

        String chat(String userMessage);
    }

    @Singleton
    public static class Calculator {

        @Tool("Calculates the length of a string")
        int stringLength(String s) {
            return s.length();
        }

        @Tool("Calculates the sum of two numbers")
        int add(int a, int b) {
            return a + b;
        }

        @Tool("Calculates the square root of a number")
        double sqrt(int x) {
            return Math.sqrt(x);
        }
    }

    @RequestScoped
    public static class ChatMemoryBean implements ChatMemoryProvider {

        private final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

        @Override
        public ChatMemory get(Object memoryId) {
            return memories.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.builder()
                    .maxMessages(20)
                    .id(memoryId)
                    .build());
        }

        @PreDestroy
        public void close() {
            memories.clear();
        }
    }
}
