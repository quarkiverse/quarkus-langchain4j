package io.quarkiverse.langchain4j.mockapi.internal.model;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.mockapi.ChatRequestView;

public record ToolDefinitionImpl(String name, String description, JsonNode parametersSchema)
        implements
            ChatRequestView.ToolDefinition {
}
