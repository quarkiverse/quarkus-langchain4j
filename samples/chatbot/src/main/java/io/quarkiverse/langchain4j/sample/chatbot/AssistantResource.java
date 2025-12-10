package io.quarkiverse.langchain4j.sample.chatbot;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.RestQuery;

@Path("assistant")
public class AssistantResource {

    private final Assistant assistant;

    public AssistantResource(Assistant assistant) {
        this.assistant = assistant;
    }

    @GET
    public Multi<String> get(
            @DefaultValue("Write a short 1 paragraph funny poem about javascript frameworks") @RestQuery String message) {
        return assistant.chat(message);
    }

    @RegisterAiService
    interface Assistant {

        Multi<String> chat(String message);
    }
}
