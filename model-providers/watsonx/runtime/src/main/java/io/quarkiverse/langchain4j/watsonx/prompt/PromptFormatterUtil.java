package io.quarkiverse.langchain4j.watsonx.prompt;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

/**
 * Utility class for handling various prompt-formatter related tasks.
 */
public class PromptFormatterUtil {

    /**
     * Converts a Map into a JSON string representation.
     *
     * @param map the map to convert
     * @return a JSON string representing the map
     */
    @SuppressWarnings("unchecked")
    public static JsonObject convert(Map<String, Object> map) {

        var json = Json.createObjectBuilder();

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map cValue) {
                json.add(key, convert(cValue));
            } else if (value instanceof Collection cValue) {
                json.add(key, convert(cValue));
            } else if (value instanceof String cValue) {
                json.add(key, cValue);
            } else if (value instanceof Integer cValue) {
                json.add(key, cValue);
            } else if (value instanceof Boolean cValue) {
                json.add(key, cValue);
            } else {
                json.add(key, value.toString());
            }
        }
        return json.build();
    }

    /**
     * Converts a ToolExecutionResultMessage into a JSON string representation.
     *
     * @param toolExecutionResultMessage the {@link ToolExecutionResultMessage} to convert
     * @return a JSON string representing the tool execution result message
     */
    public static JsonObject convert(ToolExecutionResultMessage toolExecutionResultMessage) {

        JsonValue content = null;
        if (toolExecutionResultMessage.text() != null) {
            StringReader stringReader = new StringReader(toolExecutionResultMessage.text());
            try (JsonReader jsonReader = Json.createReader(stringReader)) {
                content = jsonReader.readValue();
            }
        }

        return Json.createObjectBuilder()
                .add("content", content)
                .add("id", toolExecutionResultMessage.id())
                .build();
    }

    /**
     * Converts a {@List} of {@link ToolExecutionRequest} objects into a JSON string representation.
     *
     * @param toolExecutionRequests the {@List} of {@link ToolExecutionRequest} objects to convert
     * @return a JSON string representing the list of ToolExecutionRequest objects
     */
    public static JsonArray convert(List<ToolExecutionRequest> toolExecutionRequests) {
        var result = Json.createArrayBuilder();
        for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
            result.add(PromptFormatterUtil.convert(toolExecutionRequest));
        }
        return result.build();
    }

    /**
     * Parses a JSON string representing a {@List} of {@link ToolExecutionRequest} and converts it into a JSON representation.
     *
     * @param json the JSON string to parse
     * @return a {@List} of {@link ToolExecutionRequest} objects
     */
    public static List<ToolExecutionRequest> toolExecutionRequest(String json) {

        List<ToolExecutionRequest> result = new ArrayList<>();
        StringReader stringReader = new StringReader(json);

        try (JsonReader jsonReader = Json.createReader(stringReader)) {
            var toolExecutionRequests = jsonReader.readArray();
            for (JsonValue toolExecutionRequest : toolExecutionRequests) {
                var tool = toolExecutionRequest.asJsonObject();
                result.add(
                        ToolExecutionRequest.builder()
                                .id(UUID.randomUUID().toString())
                                .name(tool.getString("name"))
                                .arguments(tool.getJsonObject("arguments").toString())
                                .build());
            }
        }
        return result;
    }

    //
    // Converts a ToolExecutionRequest object into a JsonObject.
    //
    private static JsonObject convert(ToolExecutionRequest toolExecutionRequest) {

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
                .add("arguments", toolExecutionRequest.arguments() != null ? arguments : Json.createObjectBuilder().build())
                .build();
    }

    //
    // Converts a Collection of objects into a JsonArray.
    //
    @SuppressWarnings("unchecked")
    private static JsonArray convert(Collection<Object> list) {

        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();

        for (Object value : list) {
            if (value instanceof Map cValue) {
                jsonArrayBuilder.add(convert(cValue));
            } else if (value instanceof String cValue) {
                jsonArrayBuilder.add(cValue);
            } else if (value instanceof Integer cValue) {
                jsonArrayBuilder.add(cValue);
            } else if (value instanceof Boolean cValue) {
                jsonArrayBuilder.add(cValue);
            } else {
                jsonArrayBuilder.add(value.toString());
            }
        }
        return jsonArrayBuilder.build();
    }
}
