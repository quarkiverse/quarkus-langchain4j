package io.quarkiverse.langchain4j.gemini.common;

import java.util.Map;

public record FunctionCall(String name, Map<String, Object> args) {
}
