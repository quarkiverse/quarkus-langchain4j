package io.quarkiverse.langchain4j.test;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Since ChatMemoryStore usually is implemented via remote operations, we need to make sure
 * to keep the number of calls we make to them to a minium. This test helps lock that down
 * to what is expected.
 */
public class ChatMemoryStoreHitCountTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AiService.class, CustomChatMemoryStore.class, MirrorModelSupplier.class,
                            ChatMemoryStoreSupplier.class));

    public record HitCounts(int getMessages, int updateMessages, int deleteMessages) {
        public HitCounts subtract(HitCounts other) {
            return new HitCounts(getMessages - other.getMessages,
                    updateMessages - other.updateMessages,
                    deleteMessages - other.deleteMessages);
        }
    }

    public static class CustomChatMemoryStore extends InMemoryChatMemoryStore {

        static final AtomicInteger GET_MESSAGES_COUNT = new AtomicInteger();
        static final AtomicInteger UPDATE_MESSAGES_COUNT = new AtomicInteger();
        static final AtomicInteger DELETE_MESSAGES_COUNT = new AtomicInteger();

        static HitCounts hitCounts() {
            return new HitCounts(GET_MESSAGES_COUNT.get(), UPDATE_MESSAGES_COUNT.get(), DELETE_MESSAGES_COUNT.get());
        }

        static HitCounts measureHitCounts(Runnable r) {
            HitCounts start = hitCounts();
            r.run();
            return hitCounts().subtract(start);
        }

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            GET_MESSAGES_COUNT.incrementAndGet();
            return super.getMessages(memoryId);
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            UPDATE_MESSAGES_COUNT.incrementAndGet();
            super.updateMessages(memoryId, messages);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            DELETE_MESSAGES_COUNT.incrementAndGet();
            super.deleteMessages(memoryId);
        }
    }

    public static class MirrorModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    return ChatResponse.builder().aiMessage(new AiMessage(chatMessageToText(chatRequest.messages().get(0))))
                            .build();
                }
            };
        }
    }

    public static class ChatMemoryStoreSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return new MessageWindowChatMemory.Builder()
                            .id(memoryId)
                            .maxMessages(10)
                            .chatMemoryStore(new CustomChatMemoryStore())
                            .build();
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = MirrorModelSupplier.class, chatMemoryProviderSupplier = ChatMemoryStoreSupplier.class)
    interface AiService {
        String chat(@MemoryId String memoryId, @UserMessage String userMessage);
    }

    @Inject
    AiService service;

    @Test
    @ActivateRequestContext
    void testChatStoreHitCounts() {
        var hitCounts = CustomChatMemoryStore.measureHitCounts(() -> {
            service.chat("123", "Say hello");
        });
        // The extra hits will likely be eliminated once https://github.com/langchain4j/langchain4j/pull/4416 goes
        // in, and we update DefaultCommittableChatMemory.commit to use the set method to commit the changes.
        assertThat(hitCounts).isEqualTo(new HitCounts(3, 2, 1));
    }
}
