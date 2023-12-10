package io.quarkiverse.langchain4j.runtime.aiservice;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Unremovable;

/**
 * Creates the default {@link InMemoryChatMemoryStore} store to be used by classes annotated with {@link RegisterAiService}
 */
public class InMemoryChatMemoryStoreProducer {

    @Produces
    @Singleton
    @DefaultBean
    @Unremovable
    public InMemoryChatMemoryStore chatMemoryStore() {
        return new InMemoryChatMemoryStore();
    }
}
