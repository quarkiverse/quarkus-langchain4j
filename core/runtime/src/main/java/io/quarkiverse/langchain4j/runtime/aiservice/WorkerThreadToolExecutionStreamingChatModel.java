package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Flow;

import dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
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
 * Streaming model used by {@link dev.langchain4j.service.TokenStream} AI service methods that need a worker thread to
 * execute their tools. The upstream {@code TokenStream} executes the tools on the thread that delivers the response,
 * which is an event loop for the Vert.x based model clients, so a response requesting tools is handed to a worker
 * thread first.
 */
public class WorkerThreadToolExecutionStreamingChatModel implements StreamingChatModel {

    private final StreamingChatModel delegate;

    public WorkerThreadToolExecutionStreamingChatModel(StreamingChatModel delegate) {
        this.delegate = delegate;
    }

    public StreamingChatModel delegate() {
        return delegate;
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        delegate.chat(chatRequest, new WorkerThreadToolExecutionHandler(handler));
    }

    @Override
    public void chat(ChatRequest chatRequest, ChatRequestOptions options, StreamingChatResponseHandler handler) {
        delegate.chat(chatRequest, options, new WorkerThreadToolExecutionHandler(handler));
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        delegate.doChat(chatRequest, new WorkerThreadToolExecutionHandler(handler));
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> chat(ChatRequest chatRequest) {
        return delegate.chat(chatRequest);
    }

    @Override
    public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        return delegate.doChat(chatRequest);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private static class WorkerThreadToolExecutionHandler implements StreamingChatResponseHandler {

        private final StreamingChatResponseHandler delegate;

        WorkerThreadToolExecutionHandler(StreamingChatResponseHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            if (!Context.isOnEventLoopThread() || !completeResponse.aiMessage().hasToolExecutionRequests()) {
                delegate.onCompleteResponse(completeResponse);
                return;
            }
            Vertx.currentContext().executeBlocking(new Callable<Void>() {
                @Override
                public Void call() {
                    InternalStreamingChatResponseHandlerUtils.onCompleteResponse(delegate, completeResponse);
                    return null;
                }
            }, true);
        }

        @Override
        public void onError(Throwable error) {
            delegate.onError(error);
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            delegate.onPartialResponse(partialResponse);
        }

        @Override
        public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
            delegate.onPartialResponse(partialResponse, context);
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            delegate.onPartialThinking(partialThinking);
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
            delegate.onPartialThinking(partialThinking, context);
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
            delegate.onPartialToolCall(partialToolCall);
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
            delegate.onPartialToolCall(partialToolCall, context);
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            delegate.onCompleteToolCall(completeToolCall);
        }

        @Override
        public void onUnmappedRawEvent(Object rawEvent) {
            delegate.onUnmappedRawEvent(rawEvent);
        }
    }
}
