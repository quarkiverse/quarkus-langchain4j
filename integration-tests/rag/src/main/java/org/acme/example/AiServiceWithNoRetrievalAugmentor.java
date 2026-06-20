package org.acme.example;

import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface AiServiceWithNoRetrievalAugmentor {

    String chat(String message);

}
