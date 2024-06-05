package org.acme.example.cache;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface AiService {
    public String poem(@UserMessage("Write a poem about {topic}") String topic);
}
