package io.quarkiverse.langchain4j.compaction.runtime;

import java.util.Map;
import java.util.Objects;
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
    public String put(Object memoryId, String toolCallId, String output) {
        Map<String, String> outputs = archive.computeIfAbsent(memoryId, k -> new ConcurrentHashMap<>());
        synchronized (outputs) {
            String reference = ToolOutputArchive.reference(toolCallId, output,
                    ref -> outputs.containsKey(ref) && !Objects.equals(outputs.get(ref), output));
            outputs.putIfAbsent(reference, output);
            return reference;
        }
    }

    @Override
    public Optional<String> get(Object memoryId, String reference) {
        Map<String, String> outputs = archive.get(memoryId);
        return outputs == null ? Optional.empty() : Optional.ofNullable(outputs.get(reference));
    }

    @Override
    public void clear(Object memoryId) {
        archive.remove(memoryId);
    }
}
