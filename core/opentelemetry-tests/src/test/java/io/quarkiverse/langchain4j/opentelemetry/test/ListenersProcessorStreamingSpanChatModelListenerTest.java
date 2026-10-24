package io.quarkiverse.langchain4j.opentelemetry.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Drives the listener through the langchain4j model API with fake models. The streaming models complete or
 * fail the response on another thread, as a streaming client does: the close callback then runs long after
 * the request callback and on a different thread, which is the situation that used to leave the ended span
 * current on the calling thread. The blocking model acts as the control case and pins the parent of work
 * started in {@code doChat}.
 */
class ListenersProcessorStreamingSpanChatModelListenerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ListenersProcessorAbstractSpanChatModelListenerTest.appWithInMemorySpanExporter());

    @Inject
    SpanChatModelListener listener;
    @Inject
    Tracer tracer;
    @Inject
    InMemorySpanExporter exporter;

    private ExecutorService completionThread;

    @BeforeEach
    void setUp() {
        exporter.reset();
        completionThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-model-thread"));
    }

    @AfterEach
    void tearDown() {
        completionThread.shutdownNow();
    }

    @Test
    void streamingCompletionOnAnotherThreadKeepsTheCallersContextIntact() throws Throwable {
        onFreshThread(() -> {
            Span turn = tracer.spanBuilder("turn").startSpan();
            Span currentAfterChat;
            try (Scope ignored = turn.makeCurrent()) {
                chatAndWait(streamingModel(false));
                currentAfterChat = Span.current();
            }
            Span currentAfterClose = Span.current();
            turn.end();
            tracer.spanBuilder("next-unrelated-work").startSpan().end();

            await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(4));
            SpanData opening = span("chat fake open");
            SpanData closing = span("chat fake close");

            assertThat(opening.getParentSpanId()).isEqualTo(turn.getSpanContext().getSpanId());
            assertThat(closing.getParentSpanId()).isEqualTo(opening.getSpanId());
            assertThat(closing.getAttributes().get(AttributeKey.stringKey("gen_ai.response.id"))).isEqualTo("r1");
            assertThat(currentAfterChat.getSpanContext().getSpanId()).isEqualTo(turn.getSpanContext().getSpanId());
            assertThat(currentAfterClose.getSpanContext().isValid()).isFalse();
            assertThat(span("next-unrelated-work").getParentSpanId())
                    .isNotEqualTo(opening.getSpanId())
                    .isNotEqualTo(closing.getSpanId());
        });
    }

    @Test
    void streamingErrorOnAnotherThreadIsRecordedOnTheClosingSpan() throws Throwable {
        onFreshThread(() -> {
            Span turn = tracer.spanBuilder("turn").startSpan();
            Span currentAfterChat;
            try (Scope ignored = turn.makeCurrent()) {
                assertThatThrownBy(() -> chatAndWait(streamingModel(true)))
                        .isInstanceOf(ExecutionException.class);
                currentAfterChat = Span.current();
            }
            Span currentAfterClose = Span.current();
            turn.end();
            tracer.spanBuilder("next-unrelated-work").startSpan().end();

            await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(4));
            SpanData opening = span("chat fake open");
            SpanData closing = span("chat fake close");

            assertThat(opening.getParentSpanId()).isEqualTo(turn.getSpanContext().getSpanId());
            assertThat(closing.getParentSpanId()).isEqualTo(opening.getSpanId());
            assertThat(opening.getEvents()).isEmpty();
            assertThat(closing.getEvents())
                    .hasSize(1)
                    .first()
                    .extracting("exception")
                    .extracting("message")
                    .isEqualTo("boom");
            assertThat(currentAfterChat.getSpanContext().getSpanId()).isEqualTo(turn.getSpanContext().getSpanId());
            assertThat(currentAfterClose.getSpanContext().isValid()).isFalse();
            assertThat(span("next-unrelated-work").getParentSpanId())
                    .isNotEqualTo(opening.getSpanId())
                    .isNotEqualTo(closing.getSpanId());
        });
    }

    @Test
    void blockingModelRunsDoChatUnderTheCallersSpan() throws Throwable {
        onFreshThread(() -> {
            Span turn = tracer.spanBuilder("turn").startSpan();
            Span currentAfterChat;
            try (Scope ignored = turn.makeCurrent()) {
                blockingModel().chat(ChatRequest.builder().modelName("fake").messages(UserMessage.from("q")).build());
                currentAfterChat = Span.current();
            }
            turn.end();

            await().untilAsserted(() -> assertThat(exporter.getFinishedSpanItems()).hasSize(4));
            SpanData opening = span("chat fake open");
            SpanData closing = span("chat fake close");
            SpanData restCall = span("rest-call");

            // Pinned on purpose: by design no LLM span is open (or current) while doChat runs, so work
            // started there - e.g. the REST client span of a blocking model - is parented to the caller's
            // span, never to one of the LLM spans.
            assertThat(opening.getParentSpanId()).isEqualTo(turn.getSpanContext().getSpanId());
            assertThat(closing.getParentSpanId()).isEqualTo(opening.getSpanId());
            assertThat(restCall.getParentSpanId()).isEqualTo(turn.getSpanContext().getSpanId());
            assertThat(restCall.getParentSpanId())
                    .isNotEqualTo(opening.getSpanId())
                    .isNotEqualTo(closing.getSpanId());
            assertThat(currentAfterChat.getSpanContext().getSpanId()).isEqualTo(turn.getSpanContext().getSpanId());
        });
    }

    interface Body {
        void run() throws Exception;
    }

    private static void onFreshThread(Body body) throws Throwable {
        Throwable[] error = new Throwable[1];
        Thread thread = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                error[0] = e;
            }
        }, "caller");
        thread.start();
        thread.join();
        if (error[0] != null) {
            throw error[0];
        }
    }

    private StreamingChatModel streamingModel(boolean fail) {
        return new StreamingChatModel() {
            @Override
            public List<ChatModelListener> listeners() {
                return List.of(listener);
            }

            @Override
            public ModelProvider provider() {
                return ModelProvider.OTHER;
            }

            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                completionThread.execute(() -> {
                    if (fail) {
                        handler.onError(new RuntimeException("boom"));
                    } else {
                        handler.onCompleteResponse(ChatResponse.builder()
                                .aiMessage(AiMessage.from("hi"))
                                .id("r1")
                                .modelName("fake")
                                .build());
                    }
                });
            }
        };
    }

    private ChatModel blockingModel() {
        return new ChatModel() {
            @Override
            public List<ChatModelListener> listeners() {
                return List.of(listener);
            }

            @Override
            public ModelProvider provider() {
                return ModelProvider.OTHER;
            }

            @Override
            public ChatResponse doChat(ChatRequest request) {
                // what an HTTP-based blocking model does: the REST client span is created here
                tracer.spanBuilder("rest-call").startSpan().end();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("hi"))
                        .id("r1")
                        .modelName("fake")
                        .build();
            }
        };
    }

    private static void chatAndWait(StreamingChatModel model) throws Exception {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        model.chat(ChatRequest.builder().modelName("fake").messages(UserMessage.from("q")).build(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        future.complete(response);
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.completeExceptionally(error);
                    }
                });
        future.get(5, TimeUnit.SECONDS);
    }

    private SpanData span(String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
