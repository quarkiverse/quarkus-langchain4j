package io.quarkiverse.langchain4j.memorystore;

import java.util.Collections;
import java.util.List;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.configuration.StringConfiguration;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

/**
 * Infinispan-backed implementation of {@link ChatMemoryStore} that persists chat messages
 * as JSON strings in an Infinispan remote cache. Each conversation is stored under its
 * memory ID as a JSON array of messages.
 */
public class InfinispanChatMemoryStore implements ChatMemoryStore {

    private static final TypeReference<List<ChatMessage>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    private static final String DEFAULT_CACHE_NAME = "chat-memory";

    private static final String DEFAULT_CACHE_CONFIG = "<distributed-cache name=\"CACHE_NAME\">"
            + "<encoding media-type=\"application/x-protostream\"/>"
            + "</distributed-cache>";

    private final RemoteCache<String, String> cache;

    public InfinispanChatMemoryStore(RemoteCacheManager cacheManager, String cacheName,
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
    public void deleteMessages(Object memoryId) {
        cache.remove(memoryId.toString());
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = cache.get(memoryId.toString());
        if (json == null) {
            return Collections.emptyList();
        }
        try {
            return QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(json, MESSAGE_LIST_TYPE);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            String json = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.writeValueAsString(messages);
            cache.put(memoryId.toString(), json);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }
}
