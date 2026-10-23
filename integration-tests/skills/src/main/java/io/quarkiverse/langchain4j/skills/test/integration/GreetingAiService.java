package io.quarkiverse.langchain4j.skills.test.integration;

import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.skills.Skills;

@RegisterAiService(chatLanguageModelSupplier = StubChatModelSupplier.class)
@Skills
public interface GreetingAiService {

    String chat(String message);
}
