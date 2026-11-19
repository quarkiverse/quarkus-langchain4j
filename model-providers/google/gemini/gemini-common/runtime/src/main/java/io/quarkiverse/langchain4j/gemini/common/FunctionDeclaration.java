package io.quarkiverse.langchain4j.gemini.common;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A Gemini function declaration. Its parameters are sent either as {@code parameters}, an OpenAPI schema, or as
 * {@code parametersJsonSchema}, a JSON schema that also supports {@code $defs} and {@code $ref}.
 */
public record FunctionDeclaration(String name, String description,
        @JsonInclude(JsonInclude.Include.NON_NULL) Parameters parameters,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> parametersJsonSchema) {

    public FunctionDeclaration(String name, String description, Parameters parameters) {
        this(name, description, parameters, null);
    }

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
