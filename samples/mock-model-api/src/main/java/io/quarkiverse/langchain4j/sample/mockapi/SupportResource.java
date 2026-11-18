package io.quarkiverse.langchain4j.sample.mockapi;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/support")
public class SupportResource {

    private final SupportAssistant assistant;

    public SupportResource(SupportAssistant assistant) {
        this.assistant = assistant;
    }

    @POST
    @Path("/chat")
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(String message) {
        return assistant.chat(message);
    }

    @POST
    @Path("/triage")
    @Produces(MediaType.APPLICATION_JSON)
    public Triage triage(String message) {
        return assistant.triage(message);
    }
}
