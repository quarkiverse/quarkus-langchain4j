package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ChatHistoryStore;
import io.quarkiverse.langchain4j.RecordChatHistory;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.test.QuarkusUnitTest;

public class RecordChatHistoryTokenStreamTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(TokenStreamAiService.class,
                            TestTokenStreamChatHistoryStore.class,
                            SimpleStreamedChatModelSupplier.class,
                            NoopMemoryProviderSupplier.class));

    @Inject
    TokenStreamAiService tokenStreamService;

    @Inject
    TestTokenStreamChatHistoryStore recorder;

    @BeforeEach
    void cleanup() {
        recorder.clearEntries();
    }

    @Test
    @ActivateRequestContext
    void testTokenStreamRecording() throws InterruptedException {
        var latch = new CountDownLatch(1);
        var chunks = new CopyOnWriteArrayList<String>();

        tokenStreamService.chat("456", "Say hello")
                .onPartialResponse(chunks::add)
                .onCompleteResponse(response -> latch.countDown())
                .onError(t -> {
                    throw new RuntimeException(t);
                })
                .start();

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(chunks).containsExactly("Hi!", " ", "World!");

        List<StreamingRecordedEntry> entries = recorder.getRecordedEntries();
        assertThat(entries).hasSize(3);
        assertThat(entries.get(0)).isInstanceOfSatisfying(StreamingRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("user");
            assertThat(e.memoryId()).isEqualTo("456");
            assertThat(e.message()).isEqualTo("Say hello");
        });
        assertThat(entries.get(1)).isInstanceOfSatisfying(StreamingRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("agent");
            assertThat(e.memoryId()).isEqualTo("456");
            assertThat(e.message()).isEqualTo("Hi! World!");
        });
        assertThat(entries.get(2)).isInstanceOfSatisfying(StreamingRecordedEntry.class, e -> {
            assertThat(e.type()).isEqualTo("completed");
            assertThat(e.memoryId()).isEqualTo("456");
        });
    }

    @RecordChatHistory
    @RegisterAiService(streamingChatLanguageModelSupplier = SimpleStreamedChatModelSupplier.class, chatMemoryProviderSupplier = NoopMemoryProviderSupplier.class)
    interface TokenStreamAiService {
        TokenStream chat(@MemoryId String id, @UserMessage String msg);
    }

    public static class SimpleStreamedChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new SimpleStreamedChatModel();
        }
    }

    public static class SimpleStreamedChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse("Hi!");
            handler.onPartialResponse(" ");
            handler.onPartialResponse("World!");
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage("")).build());
        }
    }

    public static class NoopMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> new NoopChatMemory();
        }
    }

    @ApplicationScoped
    public static class TestTokenStreamChatHistoryStore implements ChatHistoryStore {

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
