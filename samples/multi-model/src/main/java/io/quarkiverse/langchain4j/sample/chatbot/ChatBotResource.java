package io.quarkiverse.langchain4j.sample.chatbot;

import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/chat")
public class ChatBotResource {

    private final Bot bot;

    public ChatBotResource(Bot bot) {
        this.bot = bot;
    }

    @POST
    public Multi<String> get(
            @DefaultValue("What can you do?") String message) {
        return bot.chat(message);
    }
}
