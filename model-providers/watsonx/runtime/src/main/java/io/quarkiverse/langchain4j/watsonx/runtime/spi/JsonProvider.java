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

    public static class MapperHolder {
        static final ObjectMapper INSTANCE = QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER
                .copy().registerModule(new WatsonxJacksonModule());
    }

    /**
     * Returns the mapper lazily. The watsonx SDK instantiates this provider via ServiceLoader in a static initializer,
     * which native-image may run at build time, so this class must not touch the Arc-backed mapper eagerly.
     */
    public static ObjectMapper mapper() {
        return MapperHolder.INSTANCE;
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper().readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, TypeToken<T> type) {
        try {
            JavaType javaType = mapper().getTypeFactory().constructType(type.getType());
            return mapper().readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJson(Object obj) {
        try {
            return mapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String prettyPrint(Object value) {
        try {
            return value instanceof String str
                    ? mapper().writerWithDefaultPrettyPrinter().writeValueAsString(mapper().readTree((str)))
                    : mapper().writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }

    @Override
    public boolean isValidObject(String json) {
        if (isNull(json) || json.isBlank())
            return false;

        try {
            JsonNode node = mapper().readTree(json);
            return node.isObject();
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
