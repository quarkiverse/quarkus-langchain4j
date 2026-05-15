package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ChatHistoryStore;
import io.quarkiverse.langchain4j.RecordChatHistory;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent;
import io.quarkiverse.langchain4j.test.streaming.StreamTestUtils;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class RecordChatHistoryChatEventStreamingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ChatEventAiService.class, StreamTestUtils.class,
                            TestChatEventChatHistoryStore.class));

    @Inject
    ChatEventAiService chatEventService;

    @Inject
    TestChatEventChatHistoryStore recorder;

    @BeforeEach
    void cleanup() {
        recorder.clearEntries();
    }

    @Test
    @ActivateRequestContext
    void testChatEventStreamingRecording() {
        List<ChatEvent> events = chatEventService.chat("789", "Say hello")
                .collect().asList().await().indefinitely();

        assertThat(events.stream()
                .filter(e -> e instanceof ChatEvent.PartialResponseEvent)
                .map(e -> ((ChatEvent.PartialResponseEvent) e).getChunk())
                .toList()).containsExactly("Hi!", " ", "World!");

        List<ChatEventRecordedEntry> entries = recorder.getRecordedEntries();
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0)).isInstanceOfSatisfying(ChatEventRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("user");
            assertThat(e.memoryId()).isEqualTo("789");
            assertThat(e.message()).isEqualTo("Say hello");
        });
        assertThat(entries.get(1)).isInstanceOfSatisfying(ChatEventRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("agent");
            assertThat(e.memoryId()).isEqualTo("789");
            assertThat(e.message()).isEqualTo("Hi! World!");
        });
        assertThat(entries.get(2)).isInstanceOfSatisfying(ChatEventRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("completed");
            assertThat(e.memoryId()).isEqualTo("789");
        });
    }

    @RecordChatHistory
    @RegisterAiService(streamingChatLanguageModelSupplier = StreamTestUtils.FakeStreamedChatModelSupplier.class, chatMemoryProviderSupplier = StreamTestUtils.FakeMemoryProviderSupplier.class)
    interface ChatEventAiService {
        Multi<ChatEvent> chat(@MemoryId String id, @UserMessage String msg);
    }

    @ApplicationScoped
    public static class TestChatEventChatHistoryStore implements ChatHistoryStore {

        private final List<ChatEventRecordedEntry> recordedEntries = new CopyOnWriteArrayList<>();

        @Override
        public void onUserMessage(Object memoryId, String userMessage) {
            recordedEntries.add(new ChatEventRecordedEntry("user", memoryId, userMessage));
        }

        @Override
        public void onAgentMessage(Object memoryId, String agentMessage) {
            recordedEntries.add(new ChatEventRecordedEntry("agent", memoryId, agentMessage));
        }

        @Override
        public void onCompleted(Object memoryId) {
            recordedEntries.add(new ChatEventRecordedEntry("completed", memoryId, null));
        }

        public List<ChatEventRecordedEntry> getRecordedEntries() {
            return recordedEntries;
        }

        public void clearEntries() {
            recordedEntries.clear();
        }
    }

    record ChatEventRecordedEntry(String type, Object memoryId, String message) {
    }
}
