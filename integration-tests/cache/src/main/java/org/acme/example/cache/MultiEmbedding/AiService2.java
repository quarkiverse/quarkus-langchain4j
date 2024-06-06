package org.acme.example.cache.MultiEmbedding;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.CacheResult;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "service2")
@CacheResult
public interface AiService2 {
    public String poem(@UserMessage("{text}") String text);
}
