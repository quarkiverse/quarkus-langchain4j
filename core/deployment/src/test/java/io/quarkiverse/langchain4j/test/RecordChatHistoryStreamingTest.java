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
import io.quarkiverse.langchain4j.test.streaming.StreamTestUtils;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;

public class RecordChatHistoryStreamingTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StreamingAiService.class, StreamTestUtils.class,
                            TestStreamingChatHistoryStore.class));

    @Inject
    StreamingAiService streamingService;

    @Inject
    TestStreamingChatHistoryStore recorder;

    @BeforeEach
    void cleanup() {
        recorder.clearEntries();
    }

    @Test
    @ActivateRequestContext
    void testStreamingRecording() {
        List<String> result = streamingService.chat("123", "Say hello")
                .collect().asList().await().indefinitely();
        assertThat(result).containsExactly("Hi!", " ", "World!");

        List<StreamingRecordedEntry> entries = recorder.getRecordedEntries();
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0)).isInstanceOfSatisfying(StreamingRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("user");
            assertThat(e.memoryId()).isEqualTo("123");
            assertThat(e.message()).isEqualTo("Say hello");
        });
        assertThat(entries.get(1)).isInstanceOfSatisfying(StreamingRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("agent");
            assertThat(e.memoryId()).isEqualTo("123");
            assertThat(e.message()).isEqualTo("Hi! World!");
        });
        assertThat(entries.get(2)).isInstanceOfSatisfying(StreamingRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("completed");
            assertThat(e.memoryId()).isEqualTo("123");
        });
    }

    @RecordChatHistory
    @RegisterAiService(streamingChatLanguageModelSupplier = StreamTestUtils.FakeStreamedChatModelSupplier.class, chatMemoryProviderSupplier = StreamTestUtils.FakeMemoryProviderSupplier.class)
    interface StreamingAiService {
        Multi<String> chat(@MemoryId String id, @UserMessage String msg);
    }

    @ApplicationScoped
    public static class TestStreamingChatHistoryStore implements ChatHistoryStore {

        private final List<StreamingRecordedEntry> recordedEntries = new CopyOnWriteArrayList<>();

        @Override
        public void onUserMessage(Object memoryId, String userMessage) {
            recordedEntries.add(new StreamingRecordedEntry("user", memoryId, userMessage));
        }

        @Override
        public void onAgentMessage(Object memoryId, String agentMessage) {
            recordedEntries.add(new StreamingRecordedEntry("agent", memoryId, agentMessage));
        }

        @Override
        public void onCompleted(Object memoryId) {
            recordedEntries.add(new StreamingRecordedEntry("completed", memoryId, null));
        }

        public List<StreamingRecordedEntry> getRecordedEntries() {
            return recordedEntries;
        }

        public void clearEntries() {
            recordedEntries.clear();
        }
    }

    record StreamingRecordedEntry(String type, Object memoryId, String message) {
    }
}
