package org.acme.example.openai.chat.ollama;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("chat-with-tools")
public class ToolChatLanguageModelResource {

    @Inject
    PropertyManagerAssistant assistant;

    @GET
    @Path("expenses")
    public String tools() {
        return assistant.answer("Rive de marne", "What is the expenses for 2023 ?");
    }

}
