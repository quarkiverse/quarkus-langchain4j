package org.acme.example.openai.aiservices;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestQuery;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;

@Path("assistant")
public class AssistantResource {

    private final Assistant assistant;

    public AssistantResource(ChatModel chatModel) {
        this.assistant = AiServices.create(Assistant.class, chatModel);
    }

    @GET
    public String get(@DefaultValue("Hello, my name is test") @RestQuery String message) {
        return assistant.chat(message);
    }

    interface Assistant {

        String chat(String message);
    }
}
