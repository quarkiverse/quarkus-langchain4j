package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ChatHistoryStore;
import io.quarkiverse.langchain4j.RecordChatHistory;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.streaming.StreamTestUtils;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;

public class RecordChatHistoryStreamingCallbacksTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StreamingAiService.class,
                            FailingAiService.class,
                            HangingAiService.class,
                            StreamTestUtils.class,
                            FailingStreamedChatModelSupplier.class,
                            HangingStreamedChatModelSupplier.class,
                            TestStreamingCallbacksStore.class));

    @Inject
    StreamingAiService streamingService;

    @Inject
    FailingAiService failingService;

    @Inject
    HangingAiService hangingService;

    @Inject
    TestStreamingCallbacksStore store;

    @BeforeEach
    void cleanup() {
        store.clearEntries();
    }

    @Test
    @ActivateRequestContext
    void testOnAgentPartialCalledForEachChunk() {
        List<String> result = streamingService.chat("p1", "Say hello")
                .collect().asList().await().indefinitely();
        assertThat(result).containsExactly("Hi!", " ", "World!");

        List<String> partials = store.getEntries().stream()
                .filter(e -> e.type().equals("partial"))
                .map(CallbackEntry::message)
                .toList();

        assertThat(partials).containsExactly("Hi!", " ", "World!");
    }

    @Test
    @ActivateRequestContext
    void testOnErrorCalledWhenStreamFails() {
        Throwable thrown = null;
        try {
            failingService.chat("e1", "Say hello")
                    .collect().asList().await().atMost(Duration.ofSeconds(5));
        } catch (Throwable t) {
            thrown = t;
        }
        assertThat(thrown).isNotNull();

        List<CallbackEntry> errors = store.getEntries().stream()
                .filter(e -> e.type().equals("error"))
                .toList();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).memoryId()).isEqualTo("e1");
        assertThat(errors.get(0).message()).isEqualTo("partial-");
    }

    @Test
    @ActivateRequestContext
    void testOnCancelledCalledWhenSubscriberCancels() throws InterruptedException {
        CountDownLatch cancelledLatch = new CountDownLatch(1);
        store.setOnCancelledLatch(cancelledLatch);

        Cancellable subscription = hangingService.chat("c1", "Say hello")
                .subscribe().with(item -> {
                }, failure -> {
                });

        subscription.cancel();

        boolean cancelledFired = cancelledLatch.await(5, TimeUnit.SECONDS);
        assertThat(cancelledFired).isTrue();

        List<CallbackEntry> cancellations = store.getEntries().stream()
                .filter(e -> e.type().equals("cancelled"))
                .toList();
        assertThat(cancellations).hasSize(1);
        assertThat(cancellations.get(0).memoryId()).isEqualTo("c1");
    }

    @RecordChatHistory
    @RegisterAiService(streamingChatLanguageModelSupplier = StreamTestUtils.FakeStreamedChatModelSupplier.class, chatMemoryProviderSupplier = StreamTestUtils.FakeMemoryProviderSupplier.class)
    interface StreamingAiService {
        Multi<String> chat(@MemoryId String id, @UserMessage String msg);
    }

    @RecordChatHistory
    @RegisterAiService(streamingChatLanguageModelSupplier = FailingStreamedChatModelSupplier.class, chatMemoryProviderSupplier = StreamTestUtils.FakeMemoryProviderSupplier.class)
    interface FailingAiService {
        Multi<String> chat(@MemoryId String id, @UserMessage String msg);
    }

    @RecordChatHistory
    @RegisterAiService(streamingChatLanguageModelSupplier = HangingStreamedChatModelSupplier.class, chatMemoryProviderSupplier = StreamTestUtils.FakeMemoryProviderSupplier.class)
    interface HangingAiService {
        Multi<String> chat(@MemoryId String id, @UserMessage String msg);
    }

    public static class FailingStreamedChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                    handler.onPartialResponse("partial-");
                    handler.onError(new RuntimeException("boom"));
                }
            };
        }
    }

    public static class HangingStreamedChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                    // never emits — gives the subscriber time to cancel
                }
            };
        }
    }

    @ApplicationScoped
    public static class TestStreamingCallbacksStore implements ChatHistoryStore {

        private final List<CallbackEntry> entries = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch onCancelledLatch;

        void setOnCancelledLatch(CountDownLatch latch) {
            this.onCancelledLatch = latch;
        }

        @Override
        public void onUserMessage(Object memoryId, String userMessage) {
            entries.add(new CallbackEntry("user", memoryId, userMessage));
        }

        @Override
        public void onAgentMessage(Object memoryId, String agentMessage) {
            entries.add(new CallbackEntry("agent", memoryId, agentMessage));
        }

        @Override
        public void onAgentPartial(Object memoryId, String chunk) {
            entries.add(new CallbackEntry("partial", memoryId, chunk));
        }

        @Override
        public void onCompleted(Object memoryId) {
            entries.add(new CallbackEntry("completed", memoryId, null));
        }

        @Override
        public void onCancelled(Object memoryId, String partialAgentMessage) {
            entries.add(new CallbackEntry("cancelled", memoryId, partialAgentMessage));
            CountDownLatch latch = onCancelledLatch;
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onError(Object memoryId, Throwable error, String partialAgentMessage) {
            entries.add(new CallbackEntry("error", memoryId, partialAgentMessage));
        }

        List<CallbackEntry> getEntries() {
            return entries;
        }

        void clearEntries() {
            entries.clear();
        }
    }

    record CallbackEntry(String type, Object memoryId, String message) {
    }
}
