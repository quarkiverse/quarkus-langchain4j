package io.quarkiverse.langchain4j.it.chat;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import dev.langchain4j.model.chat.ChatLanguageModel;

@Path("/chat")
public class ChatLanguageModelResource {

    private final ChatLanguageModel chatLanguageModel;

    public ChatLanguageModelResource(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    @GET
    @Path("blocking")
    public String blocking() {
        return chatLanguageModel.generate("When was the nobel prize for economics first awarded?");
    }
}
