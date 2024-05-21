package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.List;
import java.util.Map;

public record FunctionDeclaration(String name, String description, Parameters parameters) {

    public record Parameters(String type, Map<String, Map<String, Object>> properties, List<String> required) {

    }
}
