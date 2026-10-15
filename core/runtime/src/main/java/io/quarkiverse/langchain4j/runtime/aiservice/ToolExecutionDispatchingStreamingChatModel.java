package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
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
import dev.langchain4j.service.tool.ToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * Streaming model used by the {@code TokenStream} AI service methods. These methods are implemented by the upstream
 * LangChain4j {@code AiServiceTokenStream} and {@code AiServiceStreamingResponseHandler}, which call this model and
 * execute the requested tools on the thread that delivers the response. That thread is an event loop for the Vert.x
 * based model clients, so a response requesting tools is handed to the thread required by these tools: it stays on the
 * event loop when they can run there, and goes to a virtual thread when they all run on virtual threads, or to a worker
 * thread otherwise, including for the tools that are not known in advance, such as the ones of a {@code ToolProvider}.
 */
public class ToolExecutionDispatchingStreamingChatModel implements StreamingChatModel {

    private final StreamingChatModel delegate;
    private final QuarkusAiServiceContext context;
    private final Collection<AiServiceMethodCreateInfo> methodCreateInfos;

    public ToolExecutionDispatchingStreamingChatModel(StreamingChatModel delegate, QuarkusAiServiceContext context,
            Collection<AiServiceMethodCreateInfo> methodCreateInfos) {
        this.delegate = delegate;
        this.context = context;
        this.methodCreateInfos = methodCreateInfos;
    }

    public StreamingChatModel delegate() {
        return delegate;
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        delegate.chat(chatRequest, new ToolExecutionDispatchingHandler(handler));
    }

    @Override
    public void chat(ChatRequest chatRequest, ChatRequestOptions options, StreamingChatResponseHandler handler) {
        delegate.chat(chatRequest, options, new ToolExecutionDispatchingHandler(handler));
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        delegate.doChat(chatRequest, new ToolExecutionDispatchingHandler(handler));
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

    /**
     * The thread executing the requested tools, from the most restrictive to the most permissive one.
     */
    private enum ToolThread {
        EVENT_LOOP,
        VIRTUAL_THREAD,
        WORKER_THREAD
    }

    private ToolThread toolThread(List<ToolExecutionRequest> toolExecutionRequests) {
        ToolThread toolThread = ToolThread.EVENT_LOOP;
        for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
            toolThread = mostPermissive(toolThread, toolThread(toolExecutionRequest.name()));
        }
        return toolThread;
    }

    /**
     * Looks the tool up in the tools of the AI service and of its methods, the same name possibly being used by tools
     * with different execution models.
     */
    private ToolThread toolThread(String toolName) {
        ToolThread toolThread = toolThread(context.toolService.toolExecutors().get(toolName));
        for (AiServiceMethodCreateInfo methodCreateInfo : methodCreateInfos) {
            Map<String, ToolExecutor> toolExecutors = methodCreateInfo.getToolExecutors();
            toolThread = mostPermissive(toolThread, toolThread(toolExecutors.get(toolName)));
        }
        return toolThread != null ? toolThread : ToolThread.WORKER_THREAD;
    }

    private static ToolThread mostPermissive(ToolThread first, ToolThread second) {
        if (first == null) {
            return second;
        }
        if (second == null || first.compareTo(second) >= 0) {
            return first;
        }
        return second;
    }

    private static ToolThread toolThread(ToolExecutor toolExecutor) {
        if (toolExecutor == null) {
            return null;
        }
        if (toolExecutor instanceof QuarkusToolExecutor quarkusToolExecutor
                && quarkusToolExecutor.getMethodCreateInfo() != null) {
            ToolMethodCreateInfo methodCreateInfo = quarkusToolExecutor.getMethodCreateInfo();
            if (methodCreateInfo.canRunOnEventLoop()) {
                return ToolThread.EVENT_LOOP;
            }
            if (methodCreateInfo.executionModel() == ToolMethodCreateInfo.ExecutionModel.VIRTUAL_THREAD) {
                return ToolThread.VIRTUAL_THREAD;
            }
        }
        return ToolThread.WORKER_THREAD;
    }

    private class ToolExecutionDispatchingHandler implements StreamingChatResponseHandler {

        private final StreamingChatResponseHandler delegate;

        ToolExecutionDispatchingHandler(StreamingChatResponseHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            if (!Context.isOnEventLoopThread() || !completeResponse.aiMessage().hasToolExecutionRequests()) {
                delegate.onCompleteResponse(completeResponse);
                return;
            }
            switch (toolThread(completeResponse.aiMessage().toolExecutionRequests())) {
                case EVENT_LOOP:
                    delegate.onCompleteResponse(completeResponse);
                    break;
                case VIRTUAL_THREAD:
                    executeOnVirtualThread(completeResponse);
                    break;
                default:
                    executeOnWorkerThread(completeResponse);
            }
        }

        /**
         * Uses a duplicated context, so that the tools do not share the context locals of the root context the response
         * may be delivered on.
         */
        private void executeOnWorkerThread(ChatResponse completeResponse) {
            Context duplicatedContext = VertxContext.getOrCreateDuplicatedContext();
            if (duplicatedContext == null) {
                execute(Infrastructure.getDefaultWorkerPool(), completeResponse);
                return;
            }
            duplicatedContext.executeBlocking(new Callable<Void>() {
                @Override
                public Void call() {
                    InternalStreamingChatResponseHandlerUtils.onCompleteResponse(delegate, completeResponse);
                    return null;
                }
            }, false).onFailure(new Handler<Throwable>() {
                @Override
                public void handle(Throwable error) {
                    delegate.onError(error);
                }
            });
        }

        /**
         * The virtual thread runs on the context it is submitted from, so it is submitted from the duplicated context.
         */
        private void executeOnVirtualThread(ChatResponse completeResponse) {
            Context currentContext = Vertx.currentContext();
            Context duplicatedContext = VertxContext.getOrCreateDuplicatedContext();
            if (duplicatedContext == null || duplicatedContext == currentContext) {
                execute(VirtualThreadsRecorder.getCurrent(), completeResponse);
                return;
            }
            duplicatedContext.runOnContext(new Handler<Void>() {
                @Override
                public void handle(Void ignored) {
                    execute(VirtualThreadsRecorder.getCurrent(), completeResponse);
                }
            });
        }

        private void execute(Executor executor, ChatResponse completeResponse) {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        InternalStreamingChatResponseHandlerUtils.onCompleteResponse(delegate, completeResponse);
                    }
                });
            } catch (RejectedExecutionException e) {
                delegate.onError(e);
            }
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
