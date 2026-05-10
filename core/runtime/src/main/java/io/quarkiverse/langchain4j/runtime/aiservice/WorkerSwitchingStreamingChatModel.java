package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * Decorates a {@link StreamingChatModel} so that its callbacks are forced off the Vert.x event loop
 * when the caller subscribed from a worker / virtual thread. This preserves the Quarkus-by-default
 * "blocking-friendly emission" guarantee that the deleted {@code QuarkusAiServiceStreamingResponseHandler}
 * provided through its {@code switchToWorkerForEmission} branch.
 * <p>
 * The decision is made at {@link #chat(ChatRequest, StreamingChatResponseHandler) chat(...)} time:
 * if the caller is currently NOT on the event loop, every subsequent callback that lands on the EL
 * is hopped to a worker via {@link Context#executeBlocking(java.util.concurrent.Callable, boolean)}.
 * If the caller is already on the EL, the wrapping is a no-op (the user has accepted EL callbacks
 * by subscribing from there).
 * <p>
 * Without this decorator, providers that use {@code Vertx#runOnContext} for delivery (the typical
 * pattern for Quarkus REST clients, for example) would surface callbacks on the EL even when the
 * subscriber asked for blocking-friendly emission, breaking blocking memory stores and request-scoped
 * CDI beans inside the upstream {@code addToMemory} / {@code completeResponseHandler.accept} chain.
 */
public final class WorkerSwitchingStreamingChatModel implements StreamingChatModel {

    private final StreamingChatModel delegate;

    public WorkerSwitchingStreamingChatModel(StreamingChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public void chat(ChatRequest request, StreamingChatResponseHandler handler) {
        if (Context.isOnEventLoopThread()) {
            // Caller is on the event loop; preserve existing semantics — no thread shift.
            delegate.chat(request, handler);
            return;
        }
        // Capture a Vert.x context if one exists; otherwise fall back to a single-thread
        // executor so events still arrive in order on a non-EL thread (matches the legacy
        // QuarkusAiServiceStreamingResponseHandler executor fallback).
        Context vertxContext = Vertx.currentContext();
        delegate.chat(request, new WorkerSwitchingHandler(handler, vertxContext));
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        // The interface's default chat(...) routes through doChat — but we want the wrapping to
        // happen at the public chat(...) entry-point (where the caller's thread context is known).
        // Forward unwrapped.
        delegate.doChat(chatRequest, handler);
    }

    StreamingChatModel delegate() {
        return delegate;
    }

    private static final class WorkerSwitchingHandler implements StreamingChatResponseHandler {

        private final StreamingChatResponseHandler delegate;
        private final Context vertxContext;
        private volatile ExecutorService fallbackExecutor;

        WorkerSwitchingHandler(StreamingChatResponseHandler delegate, Context vertxContext) {
            this.delegate = delegate;
            this.vertxContext = vertxContext;
        }

        private synchronized ExecutorService fallbackExecutor() {
            if (fallbackExecutor == null) {
                // Single-thread executor preserves the per-call ordering invariant the deleted
                // QuarkusAiServiceStreamingResponseHandler relied on for its no-context branch.
                fallbackExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "quarkus-langchain4j-stream-emitter");
                    t.setDaemon(true);
                    return t;
                });
            }
            return fallbackExecutor;
        }

        private void runOffEventLoop(Runnable r) {
            if (!Context.isOnEventLoopThread()) {
                r.run();
                return;
            }
            if (vertxContext != null) {
                vertxContext.executeBlocking(() -> {
                    r.run();
                    return null;
                });
            } else {
                fallbackExecutor().submit(r);
            }
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            runOffEventLoop(() -> delegate.onPartialResponse(partialResponse));
        }

        @Override
        public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
            runOffEventLoop(() -> delegate.onPartialResponse(partialResponse, context));
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            runOffEventLoop(() -> delegate.onPartialThinking(partialThinking));
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
            runOffEventLoop(() -> delegate.onPartialThinking(partialThinking, context));
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
            runOffEventLoop(() -> delegate.onPartialToolCall(partialToolCall));
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
            runOffEventLoop(() -> delegate.onPartialToolCall(partialToolCall, context));
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            runOffEventLoop(() -> delegate.onCompleteToolCall(completeToolCall));
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            runOffEventLoop(() -> delegate.onCompleteResponse(completeResponse));
        }

        @Override
        public void onError(Throwable error) {
            runOffEventLoop(() -> delegate.onError(error));
        }
    }
}
