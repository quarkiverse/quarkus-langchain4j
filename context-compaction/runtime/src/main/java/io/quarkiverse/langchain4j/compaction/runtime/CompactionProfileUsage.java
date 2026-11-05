package io.quarkiverse.langchain4j.compaction.runtime;

import java.util.Map;

import io.quarkiverse.langchain4j.compaction.CompactionProfile;

/**
 * The AI services annotated with {@link CompactionProfile}, collected at build time.
 *
 * @param profiles profile names keyed by AI service interface name
 * @param managesChatMemory {@code true} if the extension provides the chat memory provider of the AI services,
 *        {@code false} if the application declares its own
 */
public record CompactionProfileUsage(Map<String, String> profiles, boolean managesChatMemory) {
}
