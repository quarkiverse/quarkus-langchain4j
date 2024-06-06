package org.acme.example.cache.MultiEmbedding;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.CacheResult;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "service1")
@CacheResult
public interface AiService1 {
    public String poem(@UserMessage("{text}") String text);
}
