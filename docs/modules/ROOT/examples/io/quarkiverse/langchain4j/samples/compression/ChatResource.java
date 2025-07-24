package io.quarkiverse.langchain4j.samples.compression;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/chat")
public class ChatResource {

    @Inject
    Assistant assistant;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(String question) {
        // Use the same memory ID for all questions in this demo.
        // This is just to trigger the compression logic.
        return assistant.answer("abc", question);
    }
}
