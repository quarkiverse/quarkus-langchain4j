package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/poem")
@Authenticated
public class PoemResource {

    static final String USER_MESSAGE = """
            Write a short 1 paragraph poem about Java programming language.
            Set an author name to the model or deployment name which created the poem.
            Please start by greeting the currently logged in user and asking to enjoy reading the poem, address this user by name.""";

    @RegisterAiService(tools = PoemTools.class)
    public interface VertexAiGeminiPoemService {
        @UserMessage(USER_MESSAGE)
        String writePoem();
    }
    
    @RegisterAiService(modelName= "openai", tools = PoemTools.class)
    public interface AzureOpenAiPoemService {
        @UserMessage(USER_MESSAGE)
        String writePoem();
    }
    
    @Inject
    VertexAiGeminiPoemService vertexAiGemini;

    @Inject
    AzureOpenAiPoemService azureOpenAI;

    @GET
    @Path("vertex-gemini")
    public String getPoemGemini() {
        return vertexAiGemini.writePoem();
    }

    @GET
    @Path("azure-openai")
    public String getPoemAzureOpenAI() {
        return azureOpenAI.writePoem();
    }

    @Singleton
    public static class PoemTools {

        @Inject
        SecurityIdentity identity;
    
        @Authenticated
        @Tool("Returns the name of the logged-in user")
        public String getLoggedInUserName() {
            return identity.getPrincipal().getName();
        }
        
    }
}
