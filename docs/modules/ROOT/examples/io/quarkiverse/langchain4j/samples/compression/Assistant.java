package io.quarkiverse.langchain4j.samples.compression;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
@SystemMessage("""
        You are a police and helpful assistant.
        """)
@ApplicationScoped // For demo purpose.
public interface Assistant {

    String answer(@MemoryId String id, @UserMessage String question);
}
