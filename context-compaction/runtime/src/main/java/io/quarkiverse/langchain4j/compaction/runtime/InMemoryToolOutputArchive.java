package io.quarkiverse.langchain4j.compaction.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;
import io.quarkus.arc.DefaultBean;

/**
 * The default {@link ToolOutputArchive}: keeps the outputs in memory, until the chat memory is cleared or the
 * compaction state of the conversation is evicted.
 */
@ApplicationScoped
@DefaultBean
public class InMemoryToolOutputArchive implements ToolOutputArchive {

    private final Map<Object, Map<String, String>> archive = new ConcurrentHashMap<>();

    @Override
    public void put(Object memoryId, String toolCallId, String output) {
        archive.computeIfAbsent(memoryId, k -> new ConcurrentHashMap<>()).putIfAbsent(toolCallId, output);
    }

    @Override
    public Optional<String> get(Object memoryId, String toolCallId) {
        Map<String, String> outputs = archive.get(memoryId);
        return outputs == null ? Optional.empty() : Optional.ofNullable(outputs.get(toolCallId));
    }

    @Override
    public void clear(Object memoryId) {
        archive.remove(memoryId);
    }
}
