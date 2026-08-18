package io.quarkiverse.langchain4j.sample.chatbot;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(systemMessageProviderSupplier = ModelAwareSystemMessageProvider.class)
@ApplicationScoped
public interface Bot {
    // Using Multi enables streaming.
    Multi<String> chat(@UserMessage String question);
}
