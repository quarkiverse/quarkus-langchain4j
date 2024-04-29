package io.quarkiverse.langchain4j.memorystore;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;

public class RedisChatMemoryStore implements ChatMemoryStore {

    private final ValueCommands<String, List<ChatMessage>> valueCommands;
    private final KeyCommands<String> keyCommands;

    public RedisChatMemoryStore(RedisDataSource redisDataSource) {
        this.valueCommands = redisDataSource.value(new TypeReference<List<ChatMessage>>() {
        });
        this.keyCommands = redisDataSource.key(String.class);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        keyCommands.del(memoryId.toString());

    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMessage> chatMessages = valueCommands.get(memoryId.toString());
        if (chatMessages != null) {
            return chatMessages;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        valueCommands.set(memoryId.toString(), messages);
    }
}
