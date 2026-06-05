package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ChatHistoryStore;
import io.quarkiverse.langchain4j.RecordChatHistory;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class RecordChatHistoryWithMemoryIdTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MemoryIdAiService.class, FakeChatModelSupplier.class,
                            TestMemoryIdChatHistoryStore.class));

    @Inject
    MemoryIdAiService service;

    @Inject
    TestMemoryIdChatHistoryStore recorder;

    @BeforeEach
    void cleanup() {
        recorder.clearEntries();
    }

    @Test
    @ActivateRequestContext
    void testRecordingWithMemoryId() {
        String result = service.chat("user1", "hello");
        assertThat(result).isEqualTo("Echo: hello");

        List<MemoryIdRecordedEntry> entries = recorder.getRecordedEntries();
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0)).isInstanceOfSatisfying(MemoryIdRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("user");
            assertThat(e.memoryId()).isEqualTo("user1");
            assertThat(e.message()).isEqualTo("hello");
        });
        assertThat(entries.get(1)).isInstanceOfSatisfying(MemoryIdRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("agent");
            assertThat(e.memoryId()).isEqualTo("user1");
            assertThat(e.message()).isEqualTo("Echo: hello");
        });
        assertThat(entries.get(2)).isInstanceOfSatisfying(MemoryIdRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("completed");
            assertThat(e.memoryId()).isEqualTo("user1");
        });
    }

    @Test
    @ActivateRequestContext
    void testRecordingWithDifferentMemoryIds() {
        service.chat("userA", "hi A");
        service.chat("userB", "hi B");

        List<MemoryIdRecordedEntry> entries = recorder.getRecordedEntries();
        assertThat(entries).hasSize(6);
        assertThat(entries.get(0).memoryId()).isEqualTo("userA");
        assertThat(entries.get(0).message()).isEqualTo("hi A");
        assertThat(entries.get(3).memoryId()).isEqualTo("userB");
        assertThat(entries.get(3).message()).isEqualTo("hi B");
    }

    @RecordChatHistory
    @RegisterAiService(chatLanguageModelSupplier = FakeChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface MemoryIdAiService {
        @UserMessage("{msg}")
        String chat(@MemoryId String userId, String msg);
    }

    public static class FakeChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    var lastMsg = chatRequest.messages().get(chatRequest.messages().size() - 1);
                    String userText = (lastMsg instanceof dev.langchain4j.data.message.UserMessage um) ? um.singleText()
                            : lastMsg.toString();
                    return ChatResponse.builder().aiMessage(new AiMessage("Echo: " + userText)).build();
                }
            };
        }
    }

    @ApplicationScoped
    public static class TestMemoryIdChatHistoryStore implements ChatHistoryStore {

        private final List<MemoryIdRecordedEntry> recordedEntries = new CopyOnWriteArrayList<>();

        @Override
        public void onUserMessage(Object memoryId, String userMessage) {
            recordedEntries.add(new MemoryIdRecordedEntry("user", memoryId, userMessage));
        }

        @Override
        public void onAgentMessage(Object memoryId, String agentMessage) {
            recordedEntries.add(new MemoryIdRecordedEntry("agent", memoryId, agentMessage));
        }

        @Override
        public void onCompleted(Object memoryId) {
            recordedEntries.add(new MemoryIdRecordedEntry("completed", memoryId, null));
        }

        public List<MemoryIdRecordedEntry> getRecordedEntries() {
            return recordedEntries;
        }

        public void clearEntries() {
            recordedEntries.clear();
        }
    }

    record MemoryIdRecordedEntry(String type, Object memoryId, String message) {
    }
}
