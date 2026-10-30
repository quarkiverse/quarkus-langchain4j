package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryRemovable;

public class AiServiceSerializer implements ChatScopeSerializer {
    static final ObjectMapper mapper = new ObjectMapper();
    static final ObjectReader reader = mapper.readerFor(new TypeReference<List<String>>() {
    });
    static final ObjectWriter writer = mapper.writerFor(new TypeReference<List<String>>() {
    });

    @Override
    public boolean isHandled(Object instance) {
        return instance != null && instance instanceof ChatMemoryRemovable;
    }

    @Override
    public String serialize(Object instance) {
        ChatMemoryRemovable chatMemory = (ChatMemoryRemovable) instance;
        List<String> ids = new ArrayList<>();
        for (Object id : chatMemory.getAllChatMemoryIds()) {
            ids.add(id.toString());
        }
        try {
            return writer.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object deserialize(String json, Object instance) {
        try {
            List<String> ids = reader.readValue(json);
            ChatMemoryRemovable chatMemory = (ChatMemoryRemovable) instance;
            chatMemory.manageChatMemory(ids.toArray(new String[0]));
            return chatMemory;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
