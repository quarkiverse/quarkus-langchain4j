package io.quarkiverse.langchain4j.compaction.runtime;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class CompactionRecorder {

    public Supplier<CompactionProfileUsage> usage(Map<String, String> profiles, boolean managesChatMemory,
            Set<String> retrievalToolServices) {
        return () -> new CompactionProfileUsage(Map.copyOf(profiles), managesChatMemory,
                Set.copyOf(retrievalToolServices));
    }
}
