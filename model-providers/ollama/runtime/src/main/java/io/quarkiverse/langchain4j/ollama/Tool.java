package io.quarkiverse.langchain4j.ollama;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkiverse.langchain4j.ollama.runtime.jackson.ToolTypeDeserializer;
import io.quarkiverse.langchain4j.ollama.runtime.jackson.ToolTypeSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

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

        public record Parameters(String type, Map<String, Map<String, Object>> properties, List<String> required,
                @JsonProperty("$defs") @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Map<String, Object>> defs) {

            private static final String OBJECT_TYPE = "object";

            public static Parameters objectType(Map<String, Map<String, Object>> properties, List<String> required) {
                return new Parameters(OBJECT_TYPE, properties, required, null);
            }

            /**
             * Parameters whose properties may refer to the given definitions with {@code $ref}, as LangChain4j generates
             * for a recursive parameter type.
             */
            public static Parameters objectType(Map<String, Map<String, Object>> properties, List<String> required,
                    Map<String, Map<String, Object>> defs) {
                return new Parameters(OBJECT_TYPE, properties, required, defs);
            }

            public static Parameters empty() {
                return new Parameters(OBJECT_TYPE, Collections.emptyMap(), Collections.emptyList(), null);
            }

        }
    }
}
