package io.quarkiverse.langchain4j.sample.wif;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface ChatAiService {

    @SystemMessage("You are a friendly AI assistant. Answer questions concisely.")
    String chat(@UserMessage String message);
}
