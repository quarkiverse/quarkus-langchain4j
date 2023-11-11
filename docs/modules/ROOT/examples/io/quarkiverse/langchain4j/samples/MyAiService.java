package io.quarkiverse.langchain4j.samples;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService( // <1>
        chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class, // <2>
        tools = EmailService.class // <3>
)
public interface MyAiService {

    @SystemMessage("You are a professional poet") // <4>
    @UserMessage("""
                Write a poem about {topic}. The poem should be {lines} lines long. Then send this poem by email. // <5>
            """)
    String writeAPoem(String topic, int lines); // <6>
}