package io.quarkiverse.langchain4j.memorystore;

import java.util.Collections;
import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final TypeReference<List<ChatMessage>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    private final ValueCommands<String, byte[]> valueCommands;
    private final KeyCommands<String> keyCommands;

    public RedisChatMemoryStore(RedisDataSource redisDataSource) {
        this.valueCommands = redisDataSource.value(new tools.jackson.core.type.TypeReference<byte[]>() {
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
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            valueCommands.set(memoryId.toString(),
                    QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.writeValueAsBytes(messages));
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }
}
