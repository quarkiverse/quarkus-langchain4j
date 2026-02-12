package io.quarkiverse.langchain4j.sample.assistant.resource;

import io.quarkiverse.langchain4j.sample.assistant.service.AssistantAiService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/chat")
public class ChatResource {

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance chatMessage(String response);
    }

    @Inject
    AssistantAiService aiService;

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance chat(@FormParam("message") String message) {
        String response = aiService.chat(message, 1L);
        return Templates.chatMessage(response);
    }
}
