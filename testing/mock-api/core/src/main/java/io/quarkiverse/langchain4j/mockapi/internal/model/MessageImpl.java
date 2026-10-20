package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.util.List;

import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.ToolCall;

public record MessageImpl(String role, String text, List<ToolCall> toolCalls, String toolCallId, String name)
        implements
            ChatRequestView.Message {
}
