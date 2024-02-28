package org.acme.example;

import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class)
public interface AiServiceWithNoRetrievalAugmentor {

    String chat(String message);

}
