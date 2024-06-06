package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;
import java.util.function.BiConsumer;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.AiServiceContext;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.audit.AuditService;
import io.quarkiverse.langchain4j.runtime.cache.AiCache;
import io.quarkiverse.langchain4j.runtime.cache.AiCacheProvider;

public class QuarkusAiServiceContext extends AiServiceContext {

    public AuditService auditService;
    public Map<Object, AiCache> aiCaches;
    public AiCacheProvider aiCacheProvider;

    // needed by Arc
    public QuarkusAiServiceContext() {
        super(null);
    }

    public QuarkusAiServiceContext(Class<?> aiServiceClass) {
        super(aiServiceClass);
    }

    public boolean hasCache() {
        return aiCaches != null;
    }

    public AiCache cache(Object memoryId) {
        return aiCaches.computeIfAbsent(memoryId, ignored -> aiCacheProvider.get(memoryId));
    }

    /**
     * This is called by the {@code close} method of AiServices registered with {@link RegisterAiService}
     * when the bean's scope is closed
     */
    public void close() {
        clearChatMemory();
        clearAiCache();
    }

    private void clearChatMemory() {
        if (chatMemories != null) {
            chatMemories.forEach(new BiConsumer<>() {
                @Override
                public void accept(Object memoryId, ChatMemory chatMemory) {
                    chatMemory.clear();
                }
            });
            chatMemories = null;
        }
    }

    private void clearAiCache() {
        if (aiCaches != null) {
            aiCaches.forEach(new BiConsumer<>() {
                @Override
                public void accept(Object memoryId, AiCache aiCache) {
                    aiCache.clear();
                }
            });
            aiCaches = null;
        }
    }

    /**
     * This is called by the {@code remove(Object... ids)} method of AiServices when a user manually requests removal of chat
     * memories
     * via {@link io.quarkiverse.langchain4j.ChatMemoryRemover}
     */
    public void removeChatMemoryIds(Object... ids) {
        if (chatMemories == null) {
            return;
        }
        for (Object id : ids) {
            ChatMemory chatMemory = chatMemories.remove(id);
            if (chatMemory != null) {
                chatMemory.clear();
            }
        }
    }
}
