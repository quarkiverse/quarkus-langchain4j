package io.quarkiverse.langchain4j.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryFlushStrategy;
import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.quarkus.test.QuarkusUnitTest;

public class MemoryConfigTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ConfiguredService.class, DefaultConfigService.class, MemoryProvider.class,
                            StoreConfiguredService.class, MemoryStore.class, MemoryIdProvider.class, ModelSupplier.class));

    @RegisterAiService(chatLanguageModelSupplier = ModelSupplier.class, chatMemoryProvider = MemoryProvider.class, chatMemoryFlushStrategy = ChatMemoryFlushStrategy.IMMEDIATE)
    interface ConfiguredService {

        String chat(@UserMessage String message, @MemoryId Object id);
    }

    @RegisterAiService(chatLanguageModelSupplier = ModelSupplier.class)
    interface DefaultConfigService {
        String chat(@UserMessage String message, @MemoryId Object id);
    }

    @RegisterAiService(chatLanguageModelSupplier = ModelSupplier.class, chatMemoryStore = MemoryStore.class, chatMemoryMaxMessages = 2, defaultMemoryIdProvider = MemoryIdProvider.class)
    interface StoreConfiguredService {
        String chat(@UserMessage String message);
    }

    @ApplicationScoped
    public static class MemoryProvider implements ChatMemoryProvider {

        static final AtomicBoolean USED = new AtomicBoolean();

        @Override
        public dev.langchain4j.memory.ChatMemory get(Object memoryId) {
            USED.set(true);
            return MessageWindowChatMemory.withMaxMessages(10);
        }
    }

    @ApplicationScoped
    public static class MemoryStore implements ChatMemoryStore {

        static final Map<Object, List<ChatMessage>> MESSAGES = new ConcurrentHashMap<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return new ArrayList<>(MESSAGES.getOrDefault(memoryId, List.of()));
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            MESSAGES.put(memoryId, new ArrayList<>(messages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            MESSAGES.remove(memoryId);
        }
    }

    @ApplicationScoped
    public static class MemoryIdProvider implements DefaultMemoryIdProvider {

        static final String MEMORY_ID = "configured-memory-id";

        @Override
        public Object getMemoryId() {
            return MEMORY_ID;
        }
    }

    public static class ModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    return ChatResponse.builder().aiMessage(new AiMessage("ok")).build();
                }
            };
        }
    }

    @Inject
    ConfiguredService service;

    @Inject
    DefaultConfigService defaultConfigService;

    @Inject
    StoreConfiguredService storeConfiguredService;

    @Test
    @ActivateRequestContext
    void configuresMemoryUsingBeanClass() {
        assertEquals("ok", service.chat("hello", 1));
        assertTrue(MemoryProvider.USED.get());
    }

    @Test
    @ActivateRequestContext
    void preservesDefaultMemoryConfigurationWhenNoAttributesAreSet() {
        assertEquals("ok", defaultConfigService.chat("hello", 2));
    }

    @Test
    @ActivateRequestContext
    void configuresStoreWindowAndDefaultMemoryIdUsingBeanClasses() {
        MemoryStore.MESSAGES.clear();
        storeConfiguredService.chat("one");
        storeConfiguredService.chat("two");
        storeConfiguredService.chat("three");

        assertTrue(MemoryStore.MESSAGES.containsKey(MemoryIdProvider.MEMORY_ID));
        assertEquals(2, MemoryStore.MESSAGES.get(MemoryIdProvider.MEMORY_ID).size());
    }
}
