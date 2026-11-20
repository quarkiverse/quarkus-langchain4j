package io.quarkiverse.langchain4j.mockapi;

import io.quarkiverse.langchain4j.mockapi.internal.model.ToolCallImpl;

public interface ToolCall {

    static ToolCall of(String name, String argumentsJson) {
        return of(null, name, argumentsJson);
    }

    static ToolCall of(String id, String name, String argumentsJson) {
        return new ToolCallImpl(id, name, argumentsJson);
    }

    String id();

    String name();

    String argumentsJson();
}
