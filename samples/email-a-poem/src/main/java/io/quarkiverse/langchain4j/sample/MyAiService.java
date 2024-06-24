package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(tools = EmailService.class)
public interface MyAiService {

    /**
     * Ask the LLM to create a poem about the given topic.
     *
     * @param topic the topic of the poem
     * @param lines the number of line of the poem
     * @return the poem
     */
    @SystemMessage("You are a professional poet")
    @UserMessage("""
            Write a single poem about {topic}. The poem should be {lines} lines long and your response should only include them poem itself, nothing else.
            Then send this poem by email. Your response should include the poem.
            """)
    String writeAPoem(String topic, int lines);

}
