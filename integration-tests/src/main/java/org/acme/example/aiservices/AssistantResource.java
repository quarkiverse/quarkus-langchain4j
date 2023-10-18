package org.acme.example.aiservices;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestQuery;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

@Path("assistant")
public class AssistantResource {

    private final Assistant assistant;

    public AssistantResource(ChatLanguageModel chatLanguageModel) {
        this.assistant = AiServices.create(Assistant.class, chatLanguageModel);
    }

    @GET
    public String get(@RestQuery String message) {
        return assistant.chat(message);
    }

    interface Assistant {

        String chat(String message);
    }
}
