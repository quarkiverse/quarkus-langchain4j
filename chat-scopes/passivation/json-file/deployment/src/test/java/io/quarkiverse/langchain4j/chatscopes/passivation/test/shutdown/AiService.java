package io.quarkiverse.langchain4j.chatscopes.passivation.test.shutdown;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.chatscopes.ChatScoped;

@RegisterAiService(chatLanguageModelSupplier = MirrorModelSupplier.class)
@ChatScoped
interface AiService {
    String chat(@UserMessage String userMessage);
}
