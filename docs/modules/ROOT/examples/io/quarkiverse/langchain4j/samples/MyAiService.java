package io.quarkiverse.langchain4j.samples;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService( // <1>
        tools = EmailService.class // <2>
)
public interface MyAiService {

    @SystemMessage("You are a professional poet") // <3>
    @UserMessage("""
                Write a poem about {topic}. The poem should be {lines} lines long. Then send this poem by email. // <4>
            """)
    String writeAPoem(String topic, int lines); // <5>
}
