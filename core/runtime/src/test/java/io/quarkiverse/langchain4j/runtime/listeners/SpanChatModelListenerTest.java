package io.quarkiverse.langchain4j.runtime.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.quarkiverse.langchain4j.cost.CostEstimatorService;

/**
 * Drives {@link SpanChatModelListener} through langchain4j's {@link StreamingChatModel#chat} with a fake
 * model. The control case completes the stream on the calling thread, as blocking models do; the regression
 * cases complete or fail it on another thread, as a streaming client does. When the stream completes on
 * another thread, {@code onResponse}/{@code onError} run there, and a {@link Scope} left open on the calling
 * thread by {@code onRequest} can neither be closed from that thread nor restored on the caller's, which
 * used to leave the ended LLM span current on the caller. The blocking model case pins the trade-off of
 * making the LLM span current only for the duration of {@code onRequest}: work started in {@code doChat}
 * is parented to the caller's span, not the completion span.
 */
class SpanChatModelListenerTest {

    private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
    private final SdkTracerProvider provider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(exporter))
            .build();
    private final Tracer tracer = provider.get("test");
    private final SpanChatModelListener listener = new SpanChatModelListener(tracer, new CostEstimatorService(List.of()),
            List.of());
    private final ExecutorService other = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-event-loop"));
    private final List<String> otelLog = new CopyOnWriteArrayList<>();
    private final Handler otelLogHandler = new Handler() {
        @Override
        public void publish(LogRecord r) {
            otelLog.add(Thread.currentThread().getName() + ": " + r.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    @BeforeEach
    void captureOtelContextLog() {
        Logger logger = Logger.getLogger("io.opentelemetry.context.ThreadLocalContextStorage");
        logger.setLevel(Level.ALL);
        logger.addHandler(otelLogHandler);
    }

    @AfterEach
    void tearDown() {
        Logger.getLogger("io.opentelemetry.context.ThreadLocalContextStorage").removeHandler(otelLogHandler);
        other.shutdownNow();
    }

    interface Body {
        void run() throws Exception;
    }

    private void onFreshThread(Body body) throws Throwable {
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

    private StreamingChatModel model(boolean completeOnOtherThread) {
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
                Runnable done = () -> handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from("hi"))
                        .id("r1")
                        .modelName("fake")
                        .build());
                if (completeOnOtherThread) {
                    other.execute(done);
                } else {
                    done.run();
                }
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

    private StreamingChatModel errorModel() {
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
                other.execute(() -> handler.onError(new RuntimeException("boom")));
            }
        };
    }

    private void chatAndWait(StreamingChatModel model) throws Exception {
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

    @Test
    void streamingCompletedOnAnotherThreadLeavesTheCallersContextIntact() throws Throwable {
        onFreshThread(this::crossThreadScenario);
    }

    private void crossThreadScenario() throws Exception {
        Span outer = tracer.spanBuilder("turn").startSpan();
        Span currentAfterChat;
        try (Scope ignored = outer.makeCurrent()) {
            chatAndWait(model(true));
            currentAfterChat = Span.current();
        }
        Span currentAfterOuterClosed = Span.current();
        outer.end();
        tracer.spanBuilder("next-unrelated-work").startSpan().end();

        SpanData llm = span("completion fake");
        SpanData next = span("next-unrelated-work");

        assertEquals(outer.getSpanContext().getSpanId(), llm.getParentSpanId(), "llm span parent is the turn");
        assertEquals(outer.getSpanContext().getSpanId(), currentAfterChat.getSpanContext().getSpanId(),
                "after chat() the caller's span is current again");
        assertFalse(currentAfterOuterClosed.getSpanContext().isValid(), "after the caller's scope closes no span is current");
        assertNotEquals(llm.getSpanId(), next.getParentSpanId(), "unrelated later work is not parented to the ended LLM span");
        assertTrue(otelLog.isEmpty(), "no scope is closed on the wrong thread: " + otelLog);
    }

    @Test
    void sameThreadCompletionIsClean() throws Throwable {
        onFreshThread(this::sameThreadScenario);
    }

    private void sameThreadScenario() throws Exception {
        Span outer = tracer.spanBuilder("turn").startSpan();
        Span currentAfterChat;
        try (Scope ignored = outer.makeCurrent()) {
            chatAndWait(model(false));
            currentAfterChat = Span.current();
        }
        outer.end();

        assertEquals(outer.getSpanContext().getSpanId(), currentAfterChat.getSpanContext().getSpanId());
        assertFalse(Span.current().getSpanContext().isValid());
        assertEquals(outer.getSpanContext().getSpanId(), span("completion fake").getParentSpanId());
        assertTrue(otelLog.isEmpty(), otelLog.toString());
    }

    @Test
    void blockingModelRunsDoChatUnderTheCallersSpanOnly() throws Throwable {
        onFreshThread(this::blockingModelScenario);
    }

    private void blockingModelScenario() {
        Span outer = tracer.spanBuilder("turn").startSpan();
        Span currentAfterChat;
        try (Scope ignored = outer.makeCurrent()) {
            blockingModel().chat(ChatRequest.builder().modelName("fake").messages(UserMessage.from("q")).build());
            currentAfterChat = Span.current();
        }
        outer.end();

        SpanData llm = span("completion fake");
        SpanData restCall = span("rest-call");

        // Trade-off pinned on purpose: the completion span is made current only for the duration of
        // onRequest, because for streaming models a scope opened there cannot be closed from the thread
        // that completes the stream. As a consequence, work started in doChat (e.g. the REST client span
        // of a blocking model) is parented to the caller's span instead of the completion span.
        assertEquals(outer.getSpanContext().getSpanId(), llm.getParentSpanId(), "llm span parent is the turn");
        assertEquals(outer.getSpanContext().getSpanId(), restCall.getParentSpanId(),
                "work started in doChat is parented to the caller's span, not the completion span");
        assertNotEquals(llm.getSpanId(), restCall.getParentSpanId(),
                "the ended LLM span is never left current");
        assertEquals(outer.getSpanContext().getSpanId(), currentAfterChat.getSpanContext().getSpanId(),
                "after chat() the caller's span is current again");
        assertTrue(otelLog.isEmpty(), otelLog.toString());
    }

    @Test
    void streamingErrorOnAnotherThreadEndsTheSpanAndLeavesTheCallersContextIntact() throws Throwable {
        onFreshThread(this::crossThreadErrorScenario);
    }

    private void crossThreadErrorScenario() throws Exception {
        Span outer = tracer.spanBuilder("turn").startSpan();
        Span currentAfterChat;
        try (Scope ignored = outer.makeCurrent()) {
            assertThrows(ExecutionException.class, () -> chatAndWait(errorModel()));
            currentAfterChat = Span.current();
        }
        Span currentAfterOuterClosed = Span.current();
        outer.end();
        tracer.spanBuilder("next-unrelated-work").startSpan().end();

        SpanData llm = span("completion fake");
        SpanData next = span("next-unrelated-work");

        assertEquals(outer.getSpanContext().getSpanId(), llm.getParentSpanId(), "llm span parent is the turn");
        assertTrue(llm.getEvents().stream().anyMatch(event -> event.getName().equals("exception")),
                "the error is recorded on the llm span");
        assertEquals(outer.getSpanContext().getSpanId(), currentAfterChat.getSpanContext().getSpanId(),
                "after the failed chat() the caller's span is current again");
        assertFalse(currentAfterOuterClosed.getSpanContext().isValid(), "after the caller's scope closes no span is current");
        assertNotEquals(llm.getSpanId(), next.getParentSpanId(), "unrelated later work is not parented to the ended LLM span");
        assertTrue(otelLog.isEmpty(), "no scope is closed on the wrong thread: " + otelLog);
    }
}
