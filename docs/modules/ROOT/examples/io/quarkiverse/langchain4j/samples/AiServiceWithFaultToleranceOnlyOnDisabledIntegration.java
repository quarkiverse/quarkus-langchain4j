package io.quarkiverse.langchain4j.samples;

import org.eclipse.microprofile.faulttolerance.Fallback;

import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface AiServiceWithFaultToleranceOnlyOnDisabledIntegration {
    @SystemMessage("You are a professional poet")
    @UserMessage("Write a poem about {topic}. The poem should be {lines} lines long.")
    @Fallback(fallbackMethod = "fallback", applyOn = ModelDisabledException.class)
    String writeAPoem(String topic, int lines);

    // In this case, the fallback is only applied if a ModelDisabledException is thrown
    default String fallback(String topic, int lines) {
        return "I'm sorry, I can't write a poem about " + topic;
    }
}
