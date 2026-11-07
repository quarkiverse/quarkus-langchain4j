package io.quarkiverse.langchain4j.test.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceCancelledEvent;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;

/**
 * An AI service invocation always ends with exactly one terminal observability event. LangChain4j 1.23.0 made that a
 * contract for integrations as well: {@code AiServiceCancelledEvent} was added for an invocation that is cancelled
 * before it completes, and {@code dev.langchain4j.invocation.InvocationEnd} is the shared "already ended" latch
 * upstream {@code TerminalEvents} consults before firing completed, error or cancelled, with the javadoc explicitly
 * asking "an integration that implements AI Services itself and fires these events on its own" to do the same.
 * <p>
 * The extension implements the {@code Multi} streaming path itself, and cancelling that subscription only set an
 * internal flag: no terminal event was fired at all, so an observer pairing {@code AiServiceStartedEvent} with a
 * terminal event never saw the invocation finish. Worse, aborting the stream usually makes the model provider report
 * the aborted exchange through {@code onError}, so the invocation then ended with a spurious
 * {@code AiServiceErrorEvent} for what was a deliberate cancellation.
 * <p>
 * {@code AiServiceCancelledEvent} was also missing from {@code AiServiceEvents}, the exhaustive hardcoded enum that
 * bridges langchain4j events to CDI, so {@code @Observes AiServiceCancelledEvent} could never fire either.
 */
public class StreamingCancelledEventTest {

    /**
     * Budget for something that must happen. Only a genuinely broken build should ever use it up, so it can be
     * generous without slowing a passing run down.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * How long "exactly one terminal event" has to keep holding before the assertion is believed. The window starts
     * only once the model has delivered its post-cancellation callback, which is the one event that could still turn
     * into a second terminal event.
     */
    private static final Duration SETTLE = Duration.ofMillis(500);

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StreamingAiService.class, EventCollector.class,
                            MyStreamingChatModelSupplier.class, SlowStreamingChatModel.class));

    @Inject
    StreamingAiService aiService;

    @Inject
    EventCollector events;

    @BeforeEach
    void setUp() {
        events.clear();
        SlowStreamingChatModel.MAY_FINISH = new CountDownLatch(1);
        SlowStreamingChatModel.FINISHED = new CountDownLatch(1);
        SlowStreamingChatModel.FAIL_AT_THE_END = false;
    }

    @Test
    @ActivateRequestContext
    void cancellingTheSubscriptionEndsTheInvocationWithACancelledEvent() throws InterruptedException {
        CountDownLatch firstItem = new CountDownLatch(1);

        Cancellable cancellable = aiService.chat("hello")
                .subscribe().with(item -> firstItem.countDown(), failure -> {
                });

        awaitFirstChunk(firstItem);

        cancellable.cancel();
        // let the model finish the exchange it had already started, and wait until it actually has
        SlowStreamingChatModel.MAY_FINISH.countDown();
        awaitModelExchangeFinished();

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(events.of(AiServiceStartedEvent.class)).hasSize(1);
            assertThat(events.of(AiServiceCancelledEvent.class))
                    .as("a cancelled subscription must end the invocation with an AiServiceCancelledEvent")
                    .hasSize(1);
        });
        awaitExactlyOneTerminalEvent();
    }

    @Test
    @ActivateRequestContext
    void aProviderErrorAfterTheCancellationDoesNotFireASecondTerminalEvent() throws InterruptedException {
        SlowStreamingChatModel.FAIL_AT_THE_END = true;
        CountDownLatch firstItem = new CountDownLatch(1);

        Cancellable cancellable = aiService.chat("hello")
                .subscribe().with(item -> firstItem.countDown(), failure -> {
                });

        awaitFirstChunk(firstItem);

        cancellable.cancel();
        // the aborted exchange is reported by the provider as an error, after the cancellation
        SlowStreamingChatModel.MAY_FINISH.countDown();
        awaitModelExchangeFinished();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(events.of(AiServiceCancelledEvent.class)).hasSize(1));
        awaitExactlyOneTerminalEvent();
        assertThat(events.of(AiServiceErrorEvent.class))
                .as("the aborted exchange of a cancelled invocation is not an AI service error")
                .isEmpty();
    }

    @Test
    @ActivateRequestContext
    void aStreamConsumedToTheEndStillEndsWithACompletedEvent() {
        SlowStreamingChatModel.MAY_FINISH.countDown();

        List<String> chunks = aiService.chat("hello").collect().asList().await().atMost(TIMEOUT);

        assertThat(chunks).containsExactly("Hello", " world");
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(events.of(AiServiceCompletedEvent.class)).hasSize(1));
        awaitExactlyOneTerminalEvent();
        assertThat(events.of(AiServiceCancelledEvent.class)).isEmpty();
    }

    @Test
    @ActivateRequestContext
    void aFailingStreamStillEndsWithAnErrorEvent() throws InterruptedException {
        SlowStreamingChatModel.FAIL_AT_THE_END = true;
        CountDownLatch failed = new CountDownLatch(1);

        aiService.chat("hello").subscribe().with(item -> {
        }, failure -> failed.countDown());

        SlowStreamingChatModel.MAY_FINISH.countDown();
        assertThat(failed.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .as("the failure should reach the subscriber")
                .isTrue();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(events.of(AiServiceErrorEvent.class)).hasSize(1));
        awaitExactlyOneTerminalEvent();
        assertThat(events.of(AiServiceCancelledEvent.class)).isEmpty();
    }

    private static void awaitFirstChunk(CountDownLatch firstItem) throws InterruptedException {
        assertThat(firstItem.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .as("the first chunk should reach the subscriber")
                .isTrue();
    }

    /**
     * Blocks until the mock model has handed its last callback to the extension, whether that is a completion or the
     * error a real provider reports for an aborted exchange. Anchoring the assertions here instead of on a fixed
     * delay is what makes the "no second terminal event" checks mean something: the callback that could produce one
     * has already been delivered by the time they run.
     */
    private static void awaitModelExchangeFinished() throws InterruptedException {
        assertThat(SlowStreamingChatModel.FINISHED.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .as("the model should have finished the exchange")
                .isTrue();
    }

    /**
     * Waits for the invocation's single terminal event and keeps watching for {@link #SETTLE} afterwards, so a late
     * second terminal event fails the test rather than landing just after the last assertion.
     */
    private void awaitExactlyOneTerminalEvent() {
        await().atMost(TIMEOUT)
                .during(SETTLE)
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> assertThat(events.terminal())
                        .as("exactly one terminal event per invocation")
                        .hasSize(1));
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = MyStreamingChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface StreamingAiService {
        Multi<String> chat(@UserMessage String message);
    }

    @Singleton
    public static class EventCollector {
        private final List<AiServiceEvent> received = new CopyOnWriteArrayList<>();

        void onStarted(@Observes AiServiceStartedEvent event) {
            received.add(event);
        }

        void onCompleted(@Observes AiServiceCompletedEvent event) {
            received.add(event);
        }

        void onError(@Observes AiServiceErrorEvent event) {
            received.add(event);
        }

        void onCancelled(@Observes AiServiceCancelledEvent event) {
            received.add(event);
        }

        void clear() {
            received.clear();
        }

        <T extends AiServiceEvent> List<AiServiceEvent> of(Class<T> type) {
            return received.stream().filter(type::isInstance).toList();
        }

        List<AiServiceEvent> terminal() {
            return received.stream()
                    .filter(e -> e instanceof AiServiceCompletedEvent
                            || e instanceof AiServiceErrorEvent
                            || e instanceof AiServiceCancelledEvent)
                    .toList();
        }
    }

    public static class MyStreamingChatModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new SlowStreamingChatModel();
        }
    }

    /**
     * Emits one chunk, then waits for the test before either completing the exchange or reporting it as failed,
     * which is what a real provider does once the underlying HTTP stream is aborted. {@code FINISHED} is counted down
     * once that last callback has been made, so the test never has to guess when the provider is done.
     */
    public static class SlowStreamingChatModel implements StreamingChatModel {

        static volatile CountDownLatch MAY_FINISH = new CountDownLatch(1);
        static volatile CountDownLatch FINISHED = new CountDownLatch(1);
        static volatile boolean FAIL_AT_THE_END = false;

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            boolean failAtTheEnd = FAIL_AT_THE_END;
            CountDownLatch mayFinish = MAY_FINISH;
            CountDownLatch finished = FINISHED;
            new Thread(() -> {
                try {
                    handler.onPartialResponse("Hello");
                    if (!mayFinish.await(10, TimeUnit.SECONDS)) {
                        return;
                    }
                    if (failAtTheEnd) {
                        handler.onError(new RuntimeException("the stream was aborted"));
                        return;
                    }
                    handler.onPartialResponse(" world");
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(new AiMessage("Hello world"))
                            .tokenUsage(new TokenUsage(0, 0))
                            .finishReason(FinishReason.STOP)
                            .build());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            }, "mock-streaming-model-thread").start();
        }
    }
}
