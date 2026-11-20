package io.quarkiverse.langchain4j.mockapi;

public enum FinishReason {
    STOP,
    TOOL_CALLS,
    LENGTH,
    CONTENT_FILTER
}
