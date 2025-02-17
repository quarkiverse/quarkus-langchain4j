package io.quarkiverse.langchain4j.ai.runtime.gemini;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record FunctionDeclaration(String name, String description, Parameters parameters) {

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
