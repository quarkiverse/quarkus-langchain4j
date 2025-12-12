package io.quarkiverse.langchain4j.sample.guardrails.chat;

import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ModeratedAssistant {
   @OutputGuardrails(ProfanityGuardrail.class)
   String chat(String message);
}
