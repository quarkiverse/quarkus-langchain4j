package io.quarkiverse.langchain4j.watsonx.runtime.spi;

import static java.util.Objects.isNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ibm.watsonx.ai.WatsonxJacksonModule;
import com.ibm.watsonx.ai.core.spi.json.TypeToken;

public class JsonProvider implements com.ibm.watsonx.ai.core.spi.json.JsonProvider {

    private static class MapperHolder {
        // The watsonx SDK ships WatsonxJacksonModule as a Jackson 2 module, so this integration stays on Jackson 2
        // (self-contained, not derived from the now-Jackson-3 QuarkusJsonCodecFactory holders) until the SDK
        // provides a Jackson 3 module. Snake_case + NON_NULL match the previous SnakeCaseObjectMapperHolder config.
        static final ObjectMapper INSTANCE = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                .addModule(new WatsonxJacksonModule())
                .build();
    }

    public static ObjectMapper MAPPER = MapperHolder.INSTANCE;

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, TypeToken<T> type) {
        try {
            JavaType javaType = MAPPER.getTypeFactory().constructType(type.getType());
            return MAPPER.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String prettyPrint(Object value) {
        try {
            return value instanceof String str
                    ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree((str)))
                    : MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }

    @Override
    public boolean isValidObject(String json) {
        if (isNull(json) || json.isBlank())
            return false;

        try {
            JsonNode node = MAPPER.readTree(json);
            return node.isObject();
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
