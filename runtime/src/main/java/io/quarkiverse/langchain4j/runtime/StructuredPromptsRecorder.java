package io.quarkiverse.langchain4j.runtime;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class StructuredPromptsRecorder {

    private static final Map<String, String> templates = new HashMap<>();

    public void add(String className, String template) {
        templates.put(className, template);
    }

    public static String get(String className) {
        return templates.get(className);
    }
}
