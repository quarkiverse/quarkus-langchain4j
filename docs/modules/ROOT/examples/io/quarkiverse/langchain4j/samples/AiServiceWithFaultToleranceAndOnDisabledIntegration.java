package io.quarkiverse.langchain4j.samples;

import org.eclipse.microprofile.faulttolerance.Fallback;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface AiServiceWithFaultToleranceAndOnDisabledIntegration {
    @SystemMessage("You are a professional poet")
    @UserMessage("Write a poem about {topic}. The poem should be {lines} lines long.")
    @Fallback(fallbackMethod = "fallback")
    String writeAPoem(String topic, int lines);

    // This fallback is only called when a ModelDisabledException is thrown due to disabled integration
    default String fallback(String topic, int lines, ModelDisabledException mde) {
        return "I'm sorry, but the integration with the AI provider because " + mde.getMessage();
    }

    // This fallback is called for any other exception, except for ModelDisabledException
    default String fallback(String topic, int lines) {
        return "I'm sorry, I can't write a poem about " + topic;
    }
}
