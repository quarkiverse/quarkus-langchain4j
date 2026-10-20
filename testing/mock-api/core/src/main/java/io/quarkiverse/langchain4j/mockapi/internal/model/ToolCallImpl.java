package io.quarkiverse.langchain4j.mockapi.internal.model;

import io.quarkiverse.langchain4j.mockapi.ToolCall;

public record ToolCallImpl(String id, String name, String argumentsJson) implements ToolCall {
}
