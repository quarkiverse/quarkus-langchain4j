package io.quarkiverse.langchain4j.compaction;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;

/**
 * Keeps the full text of tool outputs pruned from the context, so they can be retrieved on demand (for example by a
 * tool the agent calls with the archived id) and shown in inspection views.
 * <p>
 * This default implementation is in-memory. Replace it with a persistent store for horizontally scaled applications.
 */
@ApplicationScoped
@DefaultBean
public class ToolOutputArchive {

    private final Map<Object, Map<String, String>> archive = new ConcurrentHashMap<>();

    public void put(Object memoryId, String toolCallId, String output) {
        archive.computeIfAbsent(memoryId, k -> new ConcurrentHashMap<>()).putIfAbsent(toolCallId, output);
    }

    public Optional<String> get(Object memoryId, String toolCallId) {
        Map<String, String> outputs = archive.get(memoryId);
        return outputs == null ? Optional.empty() : Optional.ofNullable(outputs.get(toolCallId));
    }

    public void clear(Object memoryId) {
        archive.remove(memoryId);
    }
}
