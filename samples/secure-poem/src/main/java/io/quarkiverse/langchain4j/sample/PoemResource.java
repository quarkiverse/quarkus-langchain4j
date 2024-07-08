package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/poem")
@Authenticated
public class PoemResource {

    static final UserMessage USER_MESSAGE = 
        UserMessage.from("Write a short 1 paragraph poem about Java. Set an author name to the model name which created the poem.");
    
    @Inject
    ChatLanguageModel vertexAiGemini;
    
    @Inject
    @ModelName("openai")
    ChatLanguageModel azureOpenAI;
    
    @GET
    @Path("gemini")
    public String getPoemGemini() {
        Response<AiMessage> response = vertexAiGemini.generate(USER_MESSAGE);
        return response.content().text();
    }
        
    @GET
    @Path("azureopenai")
    public String getPoemAzureOpenAI() {
        Response<AiMessage> response = azureOpenAI.generate(USER_MESSAGE);
        return response.content().text();
    }
    
}
