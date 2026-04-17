package io.quarkiverse.langchain4j.chatscopes.websocket.internal;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonState {

    final ObjectMapper objectMapper;
    final Map<String, Object> data = new ConcurrentHashMap<>();

    public JsonState(ObjectMapper objectMapper, JsonNode node) {
        if (node != null) {
            node.properties().stream().forEach(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (value != null && !value.isNull()) {
                    data.put(key, value);
                }
            });
        }
        this.objectMapper = objectMapper;
    }

    public <T> T get(String key, Type type) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode) {
            try {
                Object unmarshalled = objectMapper.treeToValue((JsonNode) value, objectMapper.constructType(type));
                data.put(key, unmarshalled);
                return (T) unmarshalled;
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            return (T) value;
        }
    }

    public JsonState set(String key, Object value) {
        data.put(key, value);
        return this;
    }

    public Object remove(String key) {
        return data.remove(key);
    }
}
