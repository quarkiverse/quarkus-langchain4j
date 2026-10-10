package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class ChatMemoryReplayValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideConfigKey("quarkus.langchain4j.chat-memory.clear-on-close", "false")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, RecordingStore.class,
                            ProviderSupplier.class, RecordingChatModelSupplier.class));

    @Inject
    MyAiService service;

    @Test
    @ActivateRequestContext
    void secondRequestReplaysFirstRequestMessagesFromStore() throws Exception {
        String firstMessage = "first message content";
        service.chat("conv-1", firstMessage);
        ((AutoCloseable) service).close();
        RecordingChatModelSupplier.RECEIVED.clear();

        service.chat("conv-1", "second message");

        List<ChatMessage> sentToLlm = RecordingChatModelSupplier.RECEIVED;
        assertThat(sentToLlm)
                .as("with clear-on-close=false, a new invocation on the same memoryId "
                        + "should reload prior messages from the persistent store")
                .hasSizeGreaterThan(1);
        assertThat(sentToLlm.toString())
                .as("the reloaded messages should include the content sent in the first invocation")
                .contains(firstMessage);
    }

    @RegisterAiService(chatLanguageModelSupplier = RecordingChatModelSupplier.class, chatMemoryProviderSupplier = ProviderSupplier.class)
    public interface MyAiService {
        String chat(@MemoryId String memoryId, @UserMessage String message);
    }

    public static class RecordingStore implements ChatMemoryStore {
        static final RecordingStore INSTANCE = new RecordingStore();
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
            store.remove(memoryId);
        }
    }

    public static class ProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(50)
                    .chatMemoryStore(RecordingStore.INSTANCE)
                    .build();
        }
    }

    public static class RecordingChatModelSupplier implements Supplier<ChatModel> {
        static final List<ChatMessage> RECEIVED = new ArrayList<>();

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    RECEIVED.clear();
                    RECEIVED.addAll(chatRequest.messages());
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage("ack"))
                            .build();
                }
            };
        }
    }
}
