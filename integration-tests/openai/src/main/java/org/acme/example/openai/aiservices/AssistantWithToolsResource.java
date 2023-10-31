package org.acme.example.openai.aiservices;

import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestQuery;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

@Path("assistant-with-tool")
public class AssistantWithToolsResource {

    private final Assistant assistant;

    public AssistantWithToolsResource(ChatLanguageModel chatLanguageModel,
            Calculator calculator) {
        this.assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(calculator)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }

    @GET
    public String get(@RestQuery String message) {
        return assistant.chat(message);
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

    interface Assistant {

        String chat(String userMessage);
    }
}
