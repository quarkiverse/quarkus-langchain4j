package io.quarkiverse.langchain4j.mockapi.internal.model;

import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import tools.jackson.databind.JsonNode;

public record ToolDefinitionImpl(String name, String description, JsonNode parametersSchema)
        implements
            ChatRequestView.ToolDefinition {
}
