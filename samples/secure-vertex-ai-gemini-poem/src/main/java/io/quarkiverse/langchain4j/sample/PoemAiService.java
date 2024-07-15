package io.quarkiverse.langchain4j.sample;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface PoemAiService {

    /**
     * Ask the LLM to create a poem about Enterprise Java.
     *
     * @return the poem
     */
    @SystemMessage("You are a professional poet")
    @UserMessage("""
            Write a short 1 paragraph poem about Java. Set an author name to the model name which created the poem.
            """)
    String writeAPoem();

}
