package io.quarkiverse.langchain4j.mockapi.apptests;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@Path("/chat")
public class ChatResource {

    private final Assistant assistant;

    public ChatResource(Assistant assistant) {
        this.assistant = assistant;
    }

    @GET
    public String chat(@QueryParam("q") String q) {
        return assistant.chat(q);
    }
}
