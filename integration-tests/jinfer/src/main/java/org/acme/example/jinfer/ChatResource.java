package org.acme.example.jinfer;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import dev.langchain4j.model.chat.ChatModel;

@Path("/chat")
public class ChatResource {

    @Inject
    ChatModel chatModel;

    @GET
    @Path("/wired")
    @Produces(MediaType.TEXT_PLAIN)
    public boolean wired() {
        return chatModel != null;
    }
}
