package io.quarkiverse.langchain4j.sample.compaction;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Uses the default compaction profile (pipeline with a rolling summary). Nothing here knows about compaction.
 * <p>
 * Application scoped: a request-scoped AI service would delete its chat memory at the end of each request.
 */
@RegisterAiService
@ApplicationScoped
public interface SummarizingAssistant {

    @SystemMessage(Prompts.SYSTEM)
    String chat(@MemoryId String conversation, @UserMessage String message);
}
