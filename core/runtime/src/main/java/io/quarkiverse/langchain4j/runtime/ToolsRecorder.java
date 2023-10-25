package io.quarkiverse.langchain4j.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ToolsRecorder {

    // the key is the class' name
    private static final Map<String, List<ToolMethodCreateInfo>> metadata = new HashMap<>();

    public void setMetadata(Map<String, List<ToolMethodCreateInfo>> metadata) {
        ToolsRecorder.metadata.putAll(metadata);
    }

    public static Map<String, List<ToolMethodCreateInfo>> getMetadata() {
        return metadata;
    }

    public static void clearMetadata() {
        metadata.clear();
    }
}
