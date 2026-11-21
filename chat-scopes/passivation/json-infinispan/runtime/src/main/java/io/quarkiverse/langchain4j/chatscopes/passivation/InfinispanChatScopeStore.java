package io.quarkiverse.langchain4j.chatscopes.passivation;

import java.io.IOException;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.configuration.StringConfiguration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.chatscopes.spi.ChatScopeStore;

/**
 * Infinispan-backed implementation of {@link ChatScopeStore} that persists chat scope
 * state as JSON strings in an Infinispan remote cache. Scopes survive application
 * restarts and can be shared across multiple application instances.
 */
public class InfinispanChatScopeStore extends AbstractJsonChatScopeStore implements ChatScopeStore {

    private static final String DEFAULT_CACHE_NAME = "chat-scopes";

    private static final String DEFAULT_CACHE_CONFIG = "<distributed-cache name=\"CACHE_NAME\">"
            + "<encoding media-type=\"application/x-protostream\"/>"
            + "</distributed-cache>";

    private final RemoteCache<String, String> cache;
    private final ObjectMapper mapper = new ObjectMapper();

    public InfinispanChatScopeStore(RemoteCacheManager cacheManager, String cacheName,
            String cacheConfig, boolean createCache) {
        String name = cacheName != null ? cacheName : DEFAULT_CACHE_NAME;
        if (createCache) {
            String config = cacheConfig;
            if (config == null) {
                config = DEFAULT_CACHE_CONFIG.replace("CACHE_NAME", name);
            }
            this.cache = cacheManager.administration()
                    .getOrCreateCache(name, new StringConfiguration(config));
        } else {
            this.cache = cacheManager.getCache(name);
        }
    }

    @Override
    protected void write(ChatScopeRepresentation chatScopeEntry) throws JsonProcessingException {
        String json = mapper.writeValueAsString(chatScopeEntry);
        cache.put(chatScopeEntry.id, json);
    }

    @Override
    public ChatScopeRepresentation load(String chatScopeId) {
        if (chatScopeEntries.containsKey(chatScopeId)) {
            return chatScopeEntries.get(chatScopeId);
        }
        String json = cache.get(chatScopeId);
        if (json == null) {
            return null;
        }
        ChatScopeRepresentation entry = chatScopeEntries.computeIfAbsent(chatScopeId, new java.util.function.Function<>() {
            @Override
            public ChatScopeRepresentation apply(String id) {
                try {
                    return mapper.readValue(json, ChatScopeRepresentation.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return entry;
    }

    @Override
    public void destroy(ChatScope scope) {
        chatScopeEntries.remove(scope.getId());
        cache.remove(scope.getId());
    }
}
