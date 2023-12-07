package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.service.AiServiceContext;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.RemovableChatMemoryProvider;
import io.quarkiverse.langchain4j.audit.AuditService;

public class QuarkusAiServiceContext extends AiServiceContext {

    public AuditService auditService;

    public Set<Object> usedMemoryIds = ConcurrentHashMap.newKeySet();

    public QuarkusAiServiceContext(Class<?> aiServiceClass) {
        super(aiServiceClass);
    }

    /**
     * This is called by the {@code close} method of AiServices registered with {@link RegisterAiService}
     * when the bean's scope is closed
     */
    public void close() {
        removeChatMemories();
    }

    private void removeChatMemories() {
        if (usedMemoryIds.isEmpty()) {
            return;
        }
        RemovableChatMemoryProvider removableChatMemoryProvider = null;
        if (chatMemoryProvider instanceof RemovableChatMemoryProvider) {
            removableChatMemoryProvider = (RemovableChatMemoryProvider) chatMemoryProvider;
        }
        for (Object memoryId : usedMemoryIds) {
            if (removableChatMemoryProvider != null) {
                removableChatMemoryProvider.remove(memoryId);
            }
            chatMemories.remove(memoryId);
        }
    }
}
