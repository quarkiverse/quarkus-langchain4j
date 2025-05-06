package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/poem")
@Authenticated
public class PoemResource {

    static final String USER_MESSAGE = """
            Write a short 1 paragraph poem about a Java programming language.
            Please start by greeting the currently logged in user by name and asking to enjoy reading the poem.""";

    @RegisterAiService
    public interface GeminiPoemService {
        @UserMessage(USER_MESSAGE)
        @McpToolBox("user-name")
        String writePoem();
    }
    
    
    @Inject
    GeminiPoemService aiGemini;

    @GET
    public String getPoemGemini() {
        return aiGemini.writePoem();
    }
}
