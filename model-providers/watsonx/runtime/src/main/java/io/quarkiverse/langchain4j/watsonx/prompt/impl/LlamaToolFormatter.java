package io.quarkiverse.langchain4j.watsonx.prompt.impl;

import java.io.StringReader;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.quarkiverse.langchain4j.watsonx.prompt.PromptToolFormatter;

/**
 * The {@code LlamaToolFormatter} class is an implementation of the {@link PromptToolFormatter} interface specifically designed
 * for the family of {@code meta-llama/llama-3-x} models. This formatter is responsible for converting tool-related data into a
 * format that conforms to the expectations of the {@code meta-llama/llama-3-x} models.
 */
public class LlamaToolFormatter implements PromptToolFormatter {

    @Override
    public JsonValue convert(ToolExecutionResultMessage toolExecutionResultMessage) {
        JsonValue content = null;
        if (toolExecutionResultMessage.text() != null) {
            StringReader stringReader = new StringReader(toolExecutionResultMessage.text());
            try (JsonReader jsonReader = Json.createReader(stringReader)) {
                content = jsonReader.readValue();
            }
        }

        return Json.createObjectBuilder()
                .add("output", content)
                .build();
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
                .add("name", toolExecutionRequest.name())
                .add("parameters", toolExecutionRequest.arguments() != null ? arguments : Json.createObjectBuilder().build())
                .build();
    }

    @Override
    public String convert(List<ToolExecutionRequest> toolExecutionRequests) {
        StringJoiner joiner = new StringJoiner(";");
        for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
            joiner.add(convert(toolExecutionRequest).toString());
        }
        return joiner.toString();
    }

    @Override
    public ToolExecutionRequest toolExecutionRequest(JsonValue json) {
        var tool = json.asJsonObject();
        return ToolExecutionRequest.builder()
                .id(UUID.randomUUID().toString())
                .name(tool.getString("name"))
                .arguments(tool.getJsonObject("parameters").toString())
                .build();
    }
}
