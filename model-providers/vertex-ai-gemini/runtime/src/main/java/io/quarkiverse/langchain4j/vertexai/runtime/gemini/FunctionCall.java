package io.quarkiverse.langchain4j.vertexai.runtime.gemini;

import java.util.Map;

public record FunctionCall(String name, Map<String, String> args) {

}
