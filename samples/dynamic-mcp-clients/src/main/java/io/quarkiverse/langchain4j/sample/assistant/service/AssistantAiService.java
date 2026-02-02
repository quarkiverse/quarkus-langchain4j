package io.quarkiverse.langchain4j.sample.assistant.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService(toolProviderSupplier = McpToolProviderSupplier.class)
@ApplicationScoped
public interface AssistantAiService {

    String chat(@UserMessage String message, @MemoryId Long memoryId);
}
