package io.quarkiverse.langchain4j.samples;

import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.junit.jupiter.api.Timeout;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface AiServiceWithFaultTolerance {

    @SystemMessage("You are a professional poet")
    @UserMessage("Write a poem about {topic}. The poem should be {lines} lines long.")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @Fallback(fallbackMethod = "fallback")
    String writeAPoem(String topic, int lines);

    default String fallback(String topic, int lines) {
        return "I'm sorry, I can't write a poem about " + topic;
    }
}
