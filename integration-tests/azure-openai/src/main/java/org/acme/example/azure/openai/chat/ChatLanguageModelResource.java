package org.acme.example.azure.openai.chat;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.model.chat.ChatModel;

@Path("chat")
public class ChatLanguageModelResource {

    private final ChatModel chatModel;

    public ChatLanguageModelResource(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GET
    @Path("basic")
    public String basic() {
        return chatModel.chat("When was the nobel prize for economics first awarded?");
    }
}
