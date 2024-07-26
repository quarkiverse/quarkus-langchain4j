package io.quarkiverse.langchain4j.ollama;

import java.util.Map;

public record ToolCall(FunctionCall function) {

    public static ToolCall fromFunctionCall(String name, Map<String, Object> arguments) {
        return new ToolCall(new FunctionCall(name, arguments));
    }

    public record FunctionCall(String name, Map<String, Object> arguments) {

    }
}
