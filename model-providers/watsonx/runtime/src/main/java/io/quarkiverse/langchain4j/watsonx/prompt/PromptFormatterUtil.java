package io.quarkiverse.langchain4j.watsonx.prompt;

import java.util.Collection;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

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
