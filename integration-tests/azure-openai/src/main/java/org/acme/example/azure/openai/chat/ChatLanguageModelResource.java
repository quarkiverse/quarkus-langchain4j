package org.acme.example.azure.openai.chat;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.model.chat.ChatLanguageModel;

@Path("chat")
public class ChatLanguageModelResource {

    private final ChatLanguageModel chatLanguageModel;

    public ChatLanguageModelResource(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    @GET
    @Path("basic")
    public String basic() {
        return chatLanguageModel.chat("When was the nobel prize for economics first awarded?");
    }
}
