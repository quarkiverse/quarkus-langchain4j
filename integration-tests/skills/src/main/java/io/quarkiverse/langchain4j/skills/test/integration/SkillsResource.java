package io.quarkiverse.langchain4j.skills.test.integration;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/greeting")
public class SkillsResource {

    @Inject
    GreetingAiService greetingAiService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String greeting() {
        return greetingAiService.chat("Greet me");
    }
}
