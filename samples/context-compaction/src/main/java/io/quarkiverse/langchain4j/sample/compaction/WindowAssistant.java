package io.quarkiverse.langchain4j.sample.compaction;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.compaction.CompactionProfile;

/**
 * Same assistant, but uses the {@code window} compaction profile (keeps only the most recent turns).
 */
@RegisterAiService
@CompactionProfile("window")
@ApplicationScoped
public interface WindowAssistant {

    @SystemMessage(Prompts.SYSTEM)
    String chat(@MemoryId String conversation, @UserMessage String message);
}
