package io.quarkiverse.langchain4j.samples.usersrag;

import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;

@Path("/chat")
public class UserChatResource {

    @Inject
    UserAssistant assistant;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/chat")
    @Produces(MediaType.TEXT_PLAIN)
    @Authenticated
    public String chat() {
        return assistant.chat(username(), "What is my launch passcode?");
    }

    @GET
    @Path("/summarize")
    @Produces(MediaType.TEXT_PLAIN)
    @Authenticated
    public String summarize() {
        return assistant.summarize(username(), "Summarize the quarterly status report.");
    }

    @GET
    @Path("/stream")
    @Produces(MediaType.TEXT_PLAIN)
    @Authenticated
    public Uni<String> stream() {
        return assistant.stream(username(), "What is my launch passcode?")
                .collect().with(Collectors.joining());
    }

    private String username() {
        return identity.getPrincipal().getName();
    }
}
