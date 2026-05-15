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
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ChatHistoryStore;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class RecordChatHistoryGlobalEnabledTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SyncAiService.class, FakeChatModelSupplier.class,
                            TestChatHistoryStore.class))
            .overrideConfigKey("quarkus.langchain4j.chat-history-enabled", "true");

    @Inject
    SyncAiService syncService;

    @Inject
    TestChatHistoryStore recorder;

    @BeforeEach
    void cleanup() {
        recorder.clearEntries();
    }

    @Test
    @ActivateRequestContext
    void testGlobalConfigEnablesRecordingWithoutAnnotation() {
        String result = syncService.chat("hello global");
        assertThat(result).isEqualTo("Echo: hello global");

        List<RecordedEntry> entries = recorder.getRecordedEntries();
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0)).isInstanceOfSatisfying(RecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("user");
            assertThat(e.memoryId()).isEqualTo("default");
            assertThat(e.message()).isEqualTo("hello global");
        });
        assertThat(entries.get(1)).isInstanceOfSatisfying(RecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("agent");
            assertThat(e.memoryId()).isEqualTo("default");
            assertThat(e.message()).isEqualTo("Echo: hello global");
        });
        assertThat(entries.get(2)).isInstanceOfSatisfying(RecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("completed");
            assertThat(e.memoryId()).isEqualTo("default");
        });
    }

    @RegisterAiService(chatLanguageModelSupplier = FakeChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    interface SyncAiService {
        @UserMessage("{msg}")
        String chat(String msg);
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
    public static class TestChatHistoryStore implements ChatHistoryStore {

        private final List<RecordedEntry> recordedEntries = new CopyOnWriteArrayList<>();

        @Override
        public void onUserMessage(Object memoryId, String userMessage) {
            recordedEntries.add(new RecordedEntry("user", memoryId, userMessage));
        }

        @Override
        public void onAgentMessage(Object memoryId, String agentMessage) {
            recordedEntries.add(new RecordedEntry("agent", memoryId, agentMessage));
        }

        @Override
        public void onCompleted(Object memoryId) {
            recordedEntries.add(new RecordedEntry("completed", memoryId, null));
        }

        public List<RecordedEntry> getRecordedEntries() {
            return recordedEntries;
        }

        public void clearEntries() {
            recordedEntries.clear();
        }
    }

    record RecordedEntry(String type, Object memoryId, String message) {
    }
}
