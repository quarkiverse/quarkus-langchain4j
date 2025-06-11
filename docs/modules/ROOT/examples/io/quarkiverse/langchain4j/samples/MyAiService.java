package io.quarkiverse.langchain4j.samples;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService // <1>
@SystemMessage("You are a professional poet") // <2>
@ApplicationScoped // <3>
public interface MyAiService {

    @UserMessage("""
                Write a poem about {topic}.
                The poem should be {lines} lines long. <4>
            """)
    String writeAPoem(String topic, int lines); // <5>
}
