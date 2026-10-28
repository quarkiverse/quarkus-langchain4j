package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that when {@code quarkus.langchain4j.chat-memory.clear-on-close=false},
 * the {@link ChatMemoryStore} is not wiped when the AI service bean is closed
 * (e.g., on application shutdown), so conversations backed by a persistent store
 * survive application restarts.
 */
public class ChatMemoryClearOnCloseDisabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.langchain4j.chat-memory.clear-on-close", "false")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, CountingChatMemoryStore.class,
                            ChatMemoryProviderSupplier.class, MirrorChatModelSupplier.class));

    @Inject
    MyAiService service;

    @Test
    @ActivateRequestContext
    void closeShouldNotDeleteMessagesWhenClearOnCloseIsFalse() throws Exception {
        service.chat("user-1", "hello");

        int deletesBefore = CountingChatMemoryStore.DELETE_COUNT.get();

        ((AutoCloseable) service).close();

        int deletesAfter = CountingChatMemoryStore.DELETE_COUNT.get();
        assertThat(deletesAfter - deletesBefore)
                .as("ChatMemoryStore.deleteMessages must not be invoked on close "
                        + "when clear-on-close=false")
                .isZero();
    }

    @Test
    @ActivateRequestContext
    void closeShouldStillReleaseInMemoryReferencesWhenClearOnCloseIsFalse() throws Exception {
        service.chat("leak-test-1", "hi");
        service.chat("leak-test-2", "hi");

        ChatMemoryAccess access = (ChatMemoryAccess) service;
        assertThat(access.getChatMemory("leak-test-1")).isNotNull();

        ((AutoCloseable) service).close();

        assertThat(access.getChatMemory("leak-test-1"))
                .as("in-memory bookkeeping must be released on close to prevent leaks, "
                        + "even when the persistent store is preserved")
                .isNull();
        assertThat(access.getChatMemory("leak-test-2")).isNull();
    }

    @RegisterAiService(chatLanguageModelSupplier = MirrorChatModelSupplier.class, chatMemoryProviderSupplier = ChatMemoryProviderSupplier.class)
    public interface MyAiService {
        String chat(@MemoryId String memoryId, @UserMessage String message);
    }

    public static class CountingChatMemoryStore implements ChatMemoryStore {

        static final CountingChatMemoryStore INSTANCE = new CountingChatMemoryStore();
        static final AtomicInteger DELETE_COUNT = new AtomicInteger();

        private final ConcurrentHashMap<Object, List<ChatMessage>> store = new ConcurrentHashMap<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return store.getOrDefault(memoryId, List.of());
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            store.put(memoryId, List.copyOf(messages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            DELETE_COUNT.incrementAndGet();
            store.remove(memoryId);
        }
    }

    public static class ChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(10)
                    .chatMemoryStore(CountingChatMemoryStore.INSTANCE)
                    .build();
        }
    }

    public static class MirrorChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    ChatMessage last = chatRequest.messages().get(chatRequest.messages().size() - 1);
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage(last.toString()))
                            .build();
                }
            };
        }
    }
}
