package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import java.io.StringReader;
import java.util.UUID;

import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptToolFormatter;

/**
 * The {@code MistralLargeToolFormatter} class is an implementation of the {@link PromptToolFormatter} interface specifically
 * designed for the {@code mistralai/mistral-large} model. This formatter is responsible for converting tool-related data into a
 * format that conforms to the expectations of the {@code mistralai/mistral-large} model.
 */
public class MistralLargeToolFormatter implements PromptToolFormatter {

    @Override
    public JsonValue convert(ToolExecutionResultMessage toolExecutionResultMessage) {
        StringReader stringReader = new StringReader(toolExecutionResultMessage.text());
        try (JsonReader jsonReader = Json.createReader(stringReader)) {
            return Json.createObjectBuilder()
                    .add("content", jsonReader.readValue())
                    .add("id", toolExecutionResultMessage.id())
                    .build();
        }
    }

    @Override
    public JsonValue convert(ToolExecutionRequest toolExecutionRequest) {
        JsonValue arguments = null;
        if (toolExecutionRequest.arguments() != null) {
            StringReader stringReader = new StringReader(toolExecutionRequest.arguments());
            try (JsonReader jsonReader = Json.createReader(stringReader)) {
                arguments = jsonReader.readValue();
            }
        }

        return Json.createObjectBuilder()
                .add("id", toolExecutionRequest.id())
                .add("name", toolExecutionRequest.name())
                .add("arguments", arguments != null ? arguments : Json.createObjectBuilder().build())
                .build();
    }

    @Override
    public ToolExecutionRequest toolExecutionRequest(JsonValue jsonValue) {
        var tool = jsonValue.asJsonObject();
        return ToolExecutionRequest.builder()
                .id(UUID.randomUUID().toString())
                .name(tool.getString("name"))
                .arguments(tool.getJsonObject("arguments").toString())
                .build();
    }
}
