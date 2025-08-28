package io.quarkiverse.langchain4j.guardrails;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Deprecated(forRemoval = true)
@ApplicationScoped
public class JsonGuardrailsUtils {

    @Inject
    ObjectMapper objectMapper;

    private JsonGuardrailsUtils() {
    }

    public String trimNonJson(String llmResponse) {
        int jsonMapStart = llmResponse.indexOf('{');
        int jsonListStart = llmResponse.indexOf('[');
        if (jsonMapStart < 0 && jsonListStart < 0) {
            return null;
        }
        boolean isJsonMap = jsonMapStart >= 0 && (jsonMapStart < jsonListStart || jsonListStart < 0);

        int jsonStart = isJsonMap ? jsonMapStart : jsonListStart;
        int jsonEnd = isJsonMap ? llmResponse.lastIndexOf('}') : llmResponse.lastIndexOf(']');
        return jsonEnd >= 0 && jsonStart < jsonEnd ? llmResponse.substring(jsonStart, jsonEnd + 1) : null;
    }

    public <T> T deserialize(String json, Class<T> expectedOutputClass) {
        try {
            return objectMapper.readValue(json, expectedOutputClass);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public <T> T deserialize(String json, TypeReference<T> expectedOutputType) {
        try {
            return objectMapper.readValue(json, expectedOutputType);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
