package org.acme.example.openai.aiservices;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.faulttolerance.Fallback;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@Path("assistant-with-fallback")
public class AssistantResourceWithFallback {

    private final Assistant assistant;

    public AssistantResourceWithFallback(Assistant assistant) {
        this.assistant = assistant;
    }

    @GET
    public String get() {
        return assistant.chat("test");
    }

    @RegisterAiService
    interface Assistant {

        @SystemMessage("""
                Help me: {something}
                """)
        @Fallback(fallbackMethod = "fallback")
        String chat(String message);

        static String fallback(String message) {
            return "This is a fallback message";
        }
    }

}
