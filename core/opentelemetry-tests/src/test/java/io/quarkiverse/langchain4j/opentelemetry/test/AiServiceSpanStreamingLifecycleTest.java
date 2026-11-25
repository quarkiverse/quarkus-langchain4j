package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.awaitility.core.ThrowingRunnable;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.service.TokenStream;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceSpanAttributes;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * The span of a streaming AI service method has to cover the whole stream, not just the method call that returns it,
 * and has to be current when the model is called so the model and tool spans nest under it.
 */
class AiServiceSpanStreamingLifecycleTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClasses(StreamingAssistant.class, BlockingAssistant.class, ControlledStreamingModel.class,
                                    ControlledStreamingModelSupplier.class, RecordingChatModelSupplier.class,
                                    InMemorySpanExporterProducer.class);
                }
            })
            .overrideRuntimeConfigKey("quarkus.otel.bsp.schedule.delay", "PT0.001S")
            .overrideRuntimeConfigKey("quarkus.otel.traces.sampler", "always_on");

    @Inject
    InMemorySpanExporter exporter;

    @Inject
    StreamingAssistant streamingAssistant;

    @Inject
    BlockingAssistant blockingAssistant;

    @Inject
    Vertx vertx;

    @BeforeEach
    void reset() {
        exporter.reset();
        ControlledStreamingModel.reset();
        RecordingChatModelSupplier.seenSpan = null;
    }

    @Test
    void blockingMethodSpanIsAnInternalInvokeAiServiceSpanAndParentsTheModelCall() {
        assertThat(blockingAssistant.chat("hello"))
                .isEqualTo("blocking answer");

        var span = awaitAiServiceSpan();
        assertThat(span.getKind())
                .isEqualTo(SpanKind.INTERNAL);
        assertThat(span.getSpanId())
                .isEqualTo(RecordingChatModelSupplier.seenSpan.getSpanId());
        // recording the arguments and the result is opt-in
        assertThat(span.getAttributes().asMap())
                .doesNotContainKeys(AiServiceSpanAttributes.ARGUMENTS, AiServiceSpanAttributes.RESULT);
    }

    @Test
    void multiSpanEndsOnlyWhenTheStreamCompletes() throws Exception {
        var subscriber = streamingAssistant.stream("hello")
                .subscribe()
                .withSubscriber(AssertSubscriber.<String> create(Long.MAX_VALUE));

        assertThat(ControlledStreamingModel.called.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .isTrue();
        assertThat(aiServiceSpans())
                .isEmpty();

        ControlledStreamingModel.release.countDown();

        subscriber.awaitCompletion(TIMEOUT)
                .assertItems("streamed ", "answer");
        assertThat(awaitAiServiceSpan())
                .satisfies(new Consumer<SpanData>() {
                    @Override
                    public void accept(SpanData span) {
                        assertThat(span.getSpanId())
                                .isEqualTo(ControlledStreamingModel.seenSpan.getSpanId());
                        assertThat(span.getEvents())
                                .isEmpty();
                    }
                });
    }

    @Test
    void multiSpanRecordsTheStreamFailure() {
        ControlledStreamingModel.failure.set(new RuntimeException("--stream failed--"));
        ControlledStreamingModel.release.countDown();

        streamingAssistant.stream("hello")
                .subscribe()
                .withSubscriber(AssertSubscriber.<String> create(Long.MAX_VALUE))
                .awaitFailure(TIMEOUT)
                .assertFailedWith(RuntimeException.class, "--stream failed--");

        assertThat(awaitAiServiceSpan().getEvents())
                .singleElement()
                .extracting("exception")
                .extracting("message")
                .isEqualTo("--stream failed--");
    }

    @Test
    void multiSpanEndsWhenTheSubscriptionIsCancelled() throws Exception {
        var subscriber = streamingAssistant.stream("hello")
                .subscribe()
                .withSubscriber(AssertSubscriber.<String> create(Long.MAX_VALUE));
        assertThat(ControlledStreamingModel.called.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .isTrue();

        subscriber.cancel();

        assertThat(awaitAiServiceSpan().getEvents())
                .isEmpty();
    }

    @Test
    void eachSubscriptionToAMultiHasItsOwnSpan() {
        ControlledStreamingModel.release.countDown();
        var stream = streamingAssistant.stream("hello");

        stream.subscribe()
                .withSubscriber(AssertSubscriber.<String> create(Long.MAX_VALUE))
                .awaitCompletion(TIMEOUT)
                .assertItems("streamed ", "answer");
        stream.subscribe()
                .withSubscriber(AssertSubscriber.<String> create(Long.MAX_VALUE))
                .awaitCompletion(TIMEOUT)
                .assertItems("streamed ", "answer");

        var spans = awaitAiServiceSpans(2);
        var first = spans.get(0);
        assertThat(spans.get(1))
                .satisfies(new Consumer<SpanData>() {
                    @Override
                    public void accept(SpanData second) {
                        assertThat(second.getSpanId())
                                .isNotEqualTo(first.getSpanId())
                                .isEqualTo(ControlledStreamingModel.seenSpan.getSpanId());
                        assertThat(second.getParentSpanContext())
                                .isEqualTo(first.getParentSpanContext());
                        assertThat(second.getLinks())
                                .singleElement()
                                .extracting("spanContext")
                                .isEqualTo(first.getSpanContext());
                    }
                });
    }

    @Test
    void multiRetriedAfterAFailureRecordsTheRetryInItsOwnSpan() {
        ControlledStreamingModel.failure.set(new RuntimeException("--first attempt failed--"));
        ControlledStreamingModel.release.countDown();

        streamingAssistant.stream("hello")
                .onFailure().retry().atMost(1)
                .subscribe()
                .withSubscriber(AssertSubscriber.<String> create(Long.MAX_VALUE))
                .awaitCompletion(TIMEOUT)
                .assertItems("streamed ", "answer");

        assertThat(awaitAiServiceSpans(2))
                .satisfiesExactly(
                        new Consumer<SpanData>() {
                            @Override
                            public void accept(SpanData failedAttempt) {
                                assertThat(failedAttempt.getEvents())
                                        .singleElement()
                                        .extracting("exception")
                                        .extracting("message")
                                        .isEqualTo("--first attempt failed--");
                            }
                        },
                        new Consumer<SpanData>() {
                            @Override
                            public void accept(SpanData retry) {
                                assertThat(retry.getEvents())
                                        .isEmpty();
                                assertThat(retry.getSpanId())
                                        .isEqualTo(ControlledStreamingModel.seenSpan.getSpanId());
                            }
                        });
    }

    @Test
    void multiReturnedOnTheEventLoopParentsTheModelCall() {
        var subscriber = AssertSubscriber.<String> create(Long.MAX_VALUE);
        ControlledStreamingModel.release.countDown();

        // Requests run on a duplicated context, which carries the OTel context over to the worker thread that the
        // method moves the model call to when it is invoked on the event loop.
        VertxContext.getOrCreateDuplicatedContext(vertx.getOrCreateContext()).runOnContext(new Handler<Void>() {
            @Override
            public void handle(Void ignored) {
                streamingAssistant.stream("hello")
                        .subscribe()
                        .withSubscriber(subscriber);
            }
        });

        subscriber.awaitCompletion(TIMEOUT)
                .assertItems("streamed ", "answer");
        assertThat(awaitAiServiceSpan().getSpanId())
                .isEqualTo(ControlledStreamingModel.seenSpan.getSpanId());
    }

    @Test
    void tokenStreamSpanEndsOnlyWhenTheStreamCompletes() throws Exception {
        var completion = new CompletableFuture<ChatResponse>();
        streamingAssistant.tokenStream("hello")
                .onPartialResponse(new Consumer<String>() {
                    @Override
                    public void accept(String ignored) {
                    }
                })
                .onCompleteResponse(new Consumer<ChatResponse>() {
                    @Override
                    public void accept(ChatResponse response) {
                        completion.complete(response);
                    }
                })
                .onError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable failure) {
                        completion.completeExceptionally(failure);
                    }
                })
                .start();

        assertThat(ControlledStreamingModel.called.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .isTrue();
        assertThat(aiServiceSpans())
                .isEmpty();

        ControlledStreamingModel.release.countDown();

        assertThat(completion.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).aiMessage().text())
                .isEqualTo("streamed answer");
        assertThat(awaitAiServiceSpan().getSpanId())
                .isEqualTo(ControlledStreamingModel.seenSpan.getSpanId());
    }

    @Test
    void tokenStreamSpanEndsWhenTheCallerCancelsThroughTheStreamingHandle() {
        var completed = new AtomicBoolean(false);
        ControlledStreamingModel.release.countDown();

        streamingAssistant.tokenStream("hello")
                .onPartialResponseWithContext(new BiConsumer<PartialResponse, PartialResponseContext>() {
                    @Override
                    public void accept(PartialResponse ignored, PartialResponseContext context) {
                        context.streamingHandle().cancel();
                    }
                })
                .onCompleteResponse(new Consumer<ChatResponse>() {
                    @Override
                    public void accept(ChatResponse ignored) {
                        completed.set(true);
                    }
                })
                .onError(new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable ignored) {
                    }
                })
                .start();

        assertThat(awaitAiServiceSpan().getEvents())
                .isEmpty();
        assertThat(ControlledStreamingModel.cancelled)
                .isTrue();
        assertThat(completed)
                .isFalse();
    }

    @Test
    void tokenStreamWithIgnoredErrorsStillRecordsTheFailure() {
        ControlledStreamingModel.failure.set(new RuntimeException("--token stream failed--"));
        ControlledStreamingModel.release.countDown();

        streamingAssistant.tokenStream("hello")
                .onPartialResponse(new Consumer<String>() {
                    @Override
                    public void accept(String ignored) {
                    }
                })
                .ignoreErrors()
                .start();

        assertThat(awaitAiServiceSpan().getEvents())
                .singleElement()
                .extracting("exception")
                .extracting("message")
                .isEqualTo("--token stream failed--");
    }

    private List<SpanData> aiServiceSpans() {
        var spans = new ArrayList<SpanData>();
        for (SpanData span : exporter.getFinishedSpanItems()) {
            if (AiServiceSpanAttributes.INVOKE_AI_SERVICE
                    .equals(span.getAttributes().get(AiServiceSpanAttributes.GEN_AI_OPERATION_NAME))) {
                spans.add(span);
            }
        }
        return spans;
    }

    private SpanData awaitAiServiceSpan() {
        return awaitAiServiceSpans(1).get(0);
    }

    private List<SpanData> awaitAiServiceSpans(int count) {
        await().atMost(TIMEOUT)
                .untilAsserted(new ThrowingRunnable() {
                    @Override
                    public void run() {
                        assertThat(aiServiceSpans())
                                .hasSize(count);
                    }
                });
        return aiServiceSpans();
    }

    @ApplicationScoped
    @RegisterAiService(streamingChatLanguageModelSupplier = ControlledStreamingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface StreamingAssistant {

        Multi<String> stream(@dev.langchain4j.service.UserMessage String message);

        TokenStream tokenStream(@dev.langchain4j.service.UserMessage String message);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = RecordingChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface BlockingAssistant {

        String chat(@dev.langchain4j.service.UserMessage String message);
    }

    public static class RecordingChatModelSupplier implements Supplier<ChatModel> {

        static volatile SpanContext seenSpan;

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest request) {
                    seenSpan = Span.current().getSpanContext();
                    return ChatResponse.builder()
                            .aiMessage(AiMessage.from("blocking answer"))
                            .build();
                }
            };
        }
    }

    public static class ControlledStreamingModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new ControlledStreamingModel();
        }
    }

    /**
     * Answers from another thread once the test releases it, like a provider whose response arrives later.
     */
    public static class ControlledStreamingModel implements StreamingChatModel {

        static volatile CountDownLatch called;
        static volatile CountDownLatch release;
        static volatile SpanContext seenSpan;
        /**
         * The failure of the next call only, so a retry succeeds.
         */
        static final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        static final AtomicBoolean cancelled = new AtomicBoolean(false);

        static void reset() {
            called = new CountDownLatch(1);
            release = new CountDownLatch(1);
            seenSpan = null;
            failure.set(null);
            cancelled.set(false);
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            seenSpan = Span.current().getSpanContext();
            called.countDown();
            Thread.ofPlatform().start(new Runnable() {
                @Override
                public void run() {
                    answerOnceReleased(handler);
                }
            });
        }

        private static void answerOnceReleased(StreamingChatResponseHandler handler) {
            try {
                RuntimeException nextFailure = failure.getAndSet(null);
                var context = new PartialResponseContext(new StreamingHandle() {
                    @Override
                    public void cancel() {
                        cancelled.set(true);
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }
                });
                if (!release.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                    handler.onError(new IllegalStateException("The test never released the model"));
                } else if (nextFailure != null) {
                    handler.onError(nextFailure);
                } else {
                    // like a provider, stop streaming, without completing, once the stream is cancelled
                    handler.onPartialResponse(new PartialResponse("streamed "), context);
                    if (!cancelled.get()) {
                        handler.onPartialResponse(new PartialResponse("answer"), context);
                        handler.onCompleteResponse(ChatResponse.builder()
                                .aiMessage(AiMessage.from("streamed answer"))
                                .build());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handler.onError(e);
            }
        }
    }

    public static class InMemorySpanExporterProducer {

        @ApplicationScoped
        InMemorySpanExporter exporter() {
            return InMemorySpanExporter.create();
        }
    }
}
