package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;

public class RedisChatScopeStore extends AbstractJsonChatScopeStore implements ChatScopeStore {
    private final ValueCommands<String, byte[]> valueCommands;
    private final KeyCommands<String> keyCommands;

    public RedisChatScopeStore(RedisDataSource redisDataSource) {
        this.valueCommands = redisDataSource.value(new TypeReference<>() {
        });
        this.keyCommands = redisDataSource.key(String.class);
    }

    ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void write(ChatScopeRepresentation chatScopeEntry) throws JsonProcessingException {
        byte[] val = mapper.writeValueAsBytes(chatScopeEntry);
        valueCommands.set(chatScopeEntry.id, val);
    }

    @Override
    public ChatScopeRepresentation load(String chatScopeId) {
        if (chatScopeEntries.containsKey(chatScopeId)) {
            return chatScopeEntries.get(chatScopeId);
        }
        byte[] bytes = valueCommands.get(chatScopeId);

        if (bytes == null) {
            return null;
        }

        ChatScopeRepresentation entry = chatScopeEntries.computeIfAbsent(chatScopeId, id -> {
            try {
                return mapper.readValue(bytes, ChatScopeRepresentation.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return entry;
    }

    @Override
    public void destroy(ChatScope scope) {
        chatScopeEntries.remove(scope.getId());
        keyCommands.del(scope.getId());
    }

}
