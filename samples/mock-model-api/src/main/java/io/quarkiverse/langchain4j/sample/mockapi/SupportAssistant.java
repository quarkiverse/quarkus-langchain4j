package io.quarkiverse.langchain4j.sample.mockapi;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * The AI service under test: a support assistant that can look orders up with a tool, and that can triage an incoming
 * message into a structured {@link Triage}.
 */
@RegisterAiService(tools = OrderTools.class)
@SystemMessage("You are the support assistant of an online shop. Answer briefly, and look orders up before answering questions about them.")
public interface SupportAssistant {

    String chat(@UserMessage String message);

    @UserMessage("Triage this support message: {message}")
    Triage triage(String message);
}
