package io.quarkiverse.langchain4j.ollama;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import io.quarkiverse.langchain4j.ollama.runtime.jackson.ToolTypeDeserializer;
import io.quarkiverse.langchain4j.ollama.runtime.jackson.ToolTypeSerializer;

public record Tool(Type type, Function function) {

    public static Tool from(Function function) {
        return new Tool(Type.FUNCTION, function);
    }

    @JsonDeserialize(using = ToolTypeDeserializer.class)
    @JsonSerialize(using = ToolTypeSerializer.class)
    public enum Type {
        FUNCTION
    }

    public record Function(String name, String description, Parameters parameters) {

        public record Parameters(String type, Map<String, Map<String, Object>> properties, List<String> required) {

            private static final String OBJECT_TYPE = "object";

            public static Parameters objectType(Map<String, Map<String, Object>> properties, List<String> required) {
                return new Parameters(OBJECT_TYPE, properties, required);
            }

            public static Parameters empty() {
                return new Parameters(OBJECT_TYPE, Collections.emptyMap(), Collections.emptyList());
            }

        }
    }
}
