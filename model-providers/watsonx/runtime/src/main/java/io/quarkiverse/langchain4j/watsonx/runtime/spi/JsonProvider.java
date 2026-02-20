package io.quarkiverse.langchain4j.watsonx.runtime.spi;

import static java.util.Objects.isNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.watsonx.ai.WatsonxJacksonModule;
import com.ibm.watsonx.ai.core.spi.json.TypeToken;

import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;

public class JsonProvider implements com.ibm.watsonx.ai.core.spi.json.JsonProvider {

    public static final ObjectMapper MAPPER = QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER
            .copy().registerModule(new WatsonxJacksonModule());

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
