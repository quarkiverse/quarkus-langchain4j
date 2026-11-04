package io.quarkiverse.langchain4j.compaction.runtime;

import java.util.Map;

import io.quarkiverse.langchain4j.compaction.CompactionProfile;

/**
 * The AI services annotated with {@link CompactionProfile}, collected at build time.
 *
 * @param profiles profile names keyed by AI service interface name
 */
public record CompactionProfileUsage(Map<String, String> profiles) {
}
