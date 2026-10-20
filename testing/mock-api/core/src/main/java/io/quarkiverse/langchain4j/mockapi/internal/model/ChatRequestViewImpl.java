package io.quarkiverse.langchain4j.mockapi.internal.model;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.mockapi.ChatRequestView;

public record ChatRequestViewImpl(String modelName, boolean stream, Double temperature, Double topP, Integer maxTokens,
        List<Message> messages, List<ToolDefinition> tools, String toolChoice, String responseFormatType,
        JsonNode responseFormatSchema) implements ChatRequestView {
}
