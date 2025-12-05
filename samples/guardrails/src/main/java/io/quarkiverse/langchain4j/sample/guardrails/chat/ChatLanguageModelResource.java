package io.quarkiverse.langchain4j.sample.guardrails.chat;

import dev.langchain4j.guardrail.OutputGuardrailException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jboss.logging.Logger;

@Path("chatbot/moderated")
public class ChatLanguageModelResource {
    private static final Logger LOG = Logger.getLogger(ChatLanguageModelResource.class);

    private final ModeratedAssistant assistant;

    public ChatLanguageModelResource(ModeratedAssistant assistant) {
        this.assistant = assistant;
    }

    @POST
    public String answer(String question) {
        try {
            return assistant.chat(question);
        } catch (OutputGuardrailException exception) {
            String message = exception.getMessage();
            LOG.warn("AI generated an inappropriate message: " + message);
            if (message.contains("ProfanityGuardrail")) {
                return "[The AI answered with expletive]";
            } else {
                return "[The answer was somewhat inappropriate]";
            }
        }
    }
}
