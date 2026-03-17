package io.quarkiverse.langchain4j.sample;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("/poem")
public class PoemResource {

    @RegisterAiService
    public interface PoemAiService {

        String chat(String message);
    }

    @Inject
    PoemAiService poemAiService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    public String poem() {
        return poemAiService.chat(
                "First, activate the poem-writing skill and then write a poem following those instructions.");
    }
}
