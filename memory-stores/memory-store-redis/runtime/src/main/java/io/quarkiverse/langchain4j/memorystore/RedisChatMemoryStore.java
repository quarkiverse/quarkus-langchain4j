package io.quarkiverse.langchain4j.memorystore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;

public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final TypeReference<List<ChatMessage>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    private final ValueCommands<String, byte[]> valueCommands;
    private final KeyCommands<String> keyCommands;

    public RedisChatMemoryStore(RedisDataSource redisDataSource) {
        this.valueCommands = redisDataSource.value(new TypeReference<>() {
        });
        this.keyCommands = redisDataSource.key(String.class);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        keyCommands.del(memoryId.toString());
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        byte[] bytes = valueCommands.get(memoryId.toString());
        if (bytes == null) {
            return Collections.emptyList();
        }
        try {
            return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(
                    bytes, MESSAGE_LIST_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            valueCommands.set(memoryId.toString(),
                    QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.writeValueAsBytes(messages));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
