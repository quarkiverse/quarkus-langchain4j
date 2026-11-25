package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import io.opentelemetry.context.Scope;

/**
 * Keeps the AI service span open until the returned {@link TokenStream} completes, fails or is canceled, and hands the final
 * response to the span, which records it as the result when configured to.
 * <p>
 * Every call is forwarded to the delegate as it is made, so the delegate's own validation of how often each handler is
 * set still applies. The completion and error handlers are wrapped to end the span. {@link #ignoreErrors()} is turned
 * into an error handler that only ends the span, so errors stay ignored for the caller but are still recorded. When the
 * caller sets no completion handler, one is added at {@link #start()}. The contexts handed to the partial handlers carry
 * a {@link StreamingHandle} that also ends the span when the caller cancels through it.
 */
final class SpanEndingTokenStream implements TokenStream {

    private final TokenStream delegate;
    private final AiServiceSpan span;
    private boolean hasCompletionHandler;

    SpanEndingTokenStream(TokenStream delegate, AiServiceSpan span) {
        this.delegate = delegate;
        this.span = span;
    }

    @Override
    public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
        delegate.onPartialResponse(partialResponseHandler);
        return this;
    }

    @Override
    public TokenStream onPartialResponseWithContext(
            BiConsumer<PartialResponse, PartialResponseContext> handler) {
        delegate.onPartialResponseWithContext(new BiConsumer<PartialResponse, PartialResponseContext>() {
            @Override
            public void accept(PartialResponse partialResponse, PartialResponseContext context) {
                handler.accept(partialResponse,
                        new PartialResponseContext(spanEndingHandle(context.streamingHandle())));
            }
        });
        return this;
    }

    @Override
    public TokenStream onPartialThinking(Consumer<PartialThinking> partialThinkingHandler) {
        delegate.onPartialThinking(partialThinkingHandler);
        return this;
    }

    @Override
    public TokenStream onPartialThinkingWithContext(
            BiConsumer<PartialThinking, PartialThinkingContext> handler) {
        delegate.onPartialThinkingWithContext(new BiConsumer<PartialThinking, PartialThinkingContext>() {
            @Override
            public void accept(PartialThinking partialThinking, PartialThinkingContext context) {
                handler.accept(partialThinking,
                        new PartialThinkingContext(spanEndingHandle(context.streamingHandle())));
            }
        });
        return this;
    }

    @Override
    public TokenStream onPartialToolCall(Consumer<PartialToolCall> partialToolCallHandler) {
        delegate.onPartialToolCall(partialToolCallHandler);
        return this;
    }

    @Override
    public TokenStream onPartialToolCallWithContext(
            BiConsumer<PartialToolCall, PartialToolCallContext> handler) {
        delegate.onPartialToolCallWithContext(new BiConsumer<PartialToolCall, PartialToolCallContext>() {
            @Override
            public void accept(PartialToolCall partialToolCall, PartialToolCallContext context) {
                handler.accept(partialToolCall,
                        new PartialToolCallContext(spanEndingHandle(context.streamingHandle())));
            }
        });
        return this;
    }

    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> contentHandler) {
        delegate.onRetrieved(contentHandler);
        return this;
    }

    @Override
    public TokenStream onIntermediateResponse(Consumer<ChatResponse> intermediateResponseHandler) {
        delegate.onIntermediateResponse(intermediateResponseHandler);
        return this;
    }

    @Override
    public TokenStream beforeToolExecution(Consumer<BeforeToolExecution> beforeToolExecutionHandler) {
        delegate.beforeToolExecution(beforeToolExecutionHandler);
        return this;
    }

    @Override
    public TokenStream onUnmappedRawEvent(Consumer<Object> handler) {
        delegate.onUnmappedRawEvent(handler);
        return this;
    }

    @Override
    public TokenStream onToolExecuted(Consumer<ToolExecution> toolExecuteHandler) {
        delegate.onToolExecuted(toolExecuteHandler);
        return this;
    }

    @Override
    public TokenStream onCompleteResponse(Consumer<ChatResponse> completeResponseHandler) {
        hasCompletionHandler = true;
        delegate.onCompleteResponse(new Consumer<ChatResponse>() {
            @Override
            public void accept(ChatResponse response) {
                try {
                    completeResponseHandler.accept(response);
                } finally {
                    span.onFinalResponse(response);
                    span.completeStream();
                }
            }
        });
        return this;
    }

    @Override
    public TokenStream onError(Consumer<Throwable> errorHandler) {
        delegate.onError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable failure) {
                try {
                    errorHandler.accept(failure);
                } finally {
                    span.fail(failure);
                }
            }
        });
        return this;
    }

    @Override
    public TokenStream ignoreErrors() {
        delegate.onError(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable failure) {
                span.fail(failure);
            }
        });
        return this;
    }

    @Override
    public void start() {
        if (!hasCompletionHandler) {
            hasCompletionHandler = true;
            delegate.onCompleteResponse(new Consumer<ChatResponse>() {
                @Override
                public void accept(ChatResponse response) {
                    span.onFinalResponse(response);
                    span.completeStream();
                }
            });
        }
        try (Scope ignored = span.makeCurrent()) {
            delegate.start();
        } catch (RuntimeException | Error e) {
            span.fail(e);
            throw e;
        }
    }

    /**
     * A cancellation through the handle ends the stream without calling the completion or error handler, so the
     * handle the caller sees ends the span as well. The span ends only once the cancellation succeeded: a provider that
     * cannot cancel throws, the stream keeps running, and the completion or error handler ends the span instead.
     */
    private StreamingHandle spanEndingHandle(StreamingHandle handle) {
        return new StreamingHandle() {
            @Override
            public void cancel() {
                handle.cancel();
                span.cancel();
            }

            @Override
            public boolean isCancelled() {
                return handle.isCancelled();
            }
        };
    }
}
