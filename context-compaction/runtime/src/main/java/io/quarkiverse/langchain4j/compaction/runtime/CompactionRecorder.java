package io.quarkiverse.langchain4j.compaction.runtime;

import java.util.Map;
import java.util.function.Supplier;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class CompactionRecorder {

    public Supplier<CompactionProfileUsage> usage(Map<String, String> profiles) {
        return () -> new CompactionProfileUsage(Map.copyOf(profiles));
    }
}
