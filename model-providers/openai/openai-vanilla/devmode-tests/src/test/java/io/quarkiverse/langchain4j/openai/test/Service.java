package io.quarkiverse.langchain4j.openai.test;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface Service {

    @SystemMessage("You are a computer science conference organiser")
    @UserMessage("""
                Help me select talks that match my favorite topics: {topics}. Give me the list of talks.
            """)
    String findTalks(String topics);
}
