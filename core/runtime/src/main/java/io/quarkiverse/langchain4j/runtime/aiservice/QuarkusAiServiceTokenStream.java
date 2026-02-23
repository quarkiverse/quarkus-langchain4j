package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import io.vertx.core.Context;

/**
 * An implementation of token stream for Quarkus.
 * The only difference with the upstream implementation is the usage of the custom
 * {@link QuarkusAiServiceStreamingResponseHandler} instead of the upstream one.
 * It allows handling blocking tools execution, when we are invoked on the event loop.
 */
public class QuarkusAiServiceTokenStream implements TokenStream {

    private final List<ChatMessage> messages;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final List<Content> retrievedContents;
    private final QuarkusAiServiceContext context;
    private final InvocationContext invocationContext;
    private final Object memoryId;
    private final AiServiceMethodCreateInfo methodCreateInfo;
    private final Object[] methodArgs;
    private final Context cxtx;
    private final boolean switchToWorkerThreadForToolExecution;
    private final boolean switchToWorkerForEmission;

    private Consumer<String> partialResponseHandler;
    private Consumer<PartialThinking> partialThinkingHandler;
    private Consumer<List<Content>> contentsHandler;
    private Consumer<Throwable> errorHandler;
    private Consumer<Response<AiMessage>> completionHandler;
    private Consumer<BeforeToolExecution> beforeToolExecutionHandler;
    private Consumer<ChatResponse> intermediateResponseHandler;
    private Consumer<ToolExecution> toolExecuteHandler;
    private Consumer<ChatResponse> completeResponseHandler;

    private int onPartialResponseInvoked;
    private int onPartialThinkingInvoked;
    private int onCompleteResponseInvoked;
    private int onRetrievedInvoked;
    private int onErrorInvoked;
    private int ignoreErrorsInvoked;
    private int beforeToolExecutionInvoked;
    private int onIntermediateResponseInvoked;
    private int toolExecuteInvoked;

    private volatile QuarkusAiServiceStreamingResponseHandler handler;

    public QuarkusAiServiceTokenStream(List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            List<Content> retrievedContents,
            QuarkusAiServiceContext context, InvocationContext invocationContext,
            Object memoryId, Context ctxt, boolean switchToWorkerThreadForToolExecution,
            boolean switchToWorkerForEmission, AiServiceMethodCreateInfo methodCreateInfo,
            Object[] methodArgs) {
        this.messages = ensureNotEmpty(messages, "messages");
        this.toolSpecifications = copyIfNotNull(toolSpecifications);
        this.toolExecutors = copyIfNotNull(toolExecutors);
        this.retrievedContents = retrievedContents;
        this.context = ensureNotNull(context, "context");
        this.invocationContext = ensureNotNull(invocationContext, "invocationContext");
        this.memoryId = ensureNotNull(memoryId, "memoryId");
        this.methodCreateInfo = methodCreateInfo;
        this.methodArgs = methodArgs;
        ensureNotNull(context.streamingChatModel, "streamingChatModel");
        this.cxtx = ctxt; // If set, it means we need to handle the context propagation.
        this.switchToWorkerThreadForToolExecution = switchToWorkerThreadForToolExecution; // If true, we need to switch to a worker thread to execute tools.
        this.switchToWorkerForEmission = switchToWorkerForEmission;
    }

    @Override
    public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
        this.partialResponseHandler = partialResponseHandler;
        this.onPartialResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream onPartialThinking(Consumer<PartialThinking> partialThinkingHandler) {
        this.partialThinkingHandler = partialThinkingHandler;
        this.onPartialThinkingInvoked++;
        return this;
    }

    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> contentsHandler) {
        this.contentsHandler = contentsHandler;
        this.onRetrievedInvoked++;
        return this;
    }

    @Override
    public TokenStream beforeToolExecution(Consumer<BeforeToolExecution> beforeToolExecutionHandler) {
        this.beforeToolExecutionHandler = beforeToolExecutionHandler;
        this.beforeToolExecutionInvoked++;
        return this;
    }

    @Override
    public TokenStream onToolExecuted(Consumer<ToolExecution> toolExecuteHandler) {
        this.toolExecuteHandler = toolExecuteHandler;
        this.toolExecuteInvoked++;
        return this;
    }

    @Override
    public TokenStream onIntermediateResponse(Consumer<ChatResponse> intermediateResponseHandler) {
        this.intermediateResponseHandler = intermediateResponseHandler;
        this.onIntermediateResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream onCompleteResponse(Consumer<ChatResponse> completionHandler) {
        this.completeResponseHandler = completionHandler;
        this.onCompleteResponseInvoked++;
        return this;
    }

    @Override
    public TokenStream onError(Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
        this.onErrorInvoked++;
        return this;
    }

    @Override
    public TokenStream ignoreErrors() {
        this.errorHandler = null;
        this.ignoreErrorsInvoked++;
        return this;
    }

    @Override
    public void start() {
        validateConfiguration();
        ChatRequest chatRequest = new ChatRequest.Builder()
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();

        this.handler = new QuarkusAiServiceStreamingResponseHandler(
                chatRequest,
                context,
                invocationContext,
                memoryId,
                partialResponseHandler,
                partialThinkingHandler,
                beforeToolExecutionHandler,
                intermediateResponseHandler,
                toolExecuteHandler,
                completeResponseHandler,
                completionHandler,
                errorHandler,
                initTemporaryMemory(context, messages),
                new TokenUsage(),
                toolSpecifications,
                toolExecutors,
                switchToWorkerThreadForToolExecution,
                switchToWorkerForEmission,
                cxtx, methodCreateInfo, methodArgs);

        if (contentsHandler != null && retrievedContents != null) {
            contentsHandler.accept(retrievedContents);
        }

        context.eventListenerRegistrar.fireEvent(
                AiServiceRequestIssuedEvent.builder()
                        .invocationContext(invocationContext)
                        .request(chatRequest)
                        .build());

        try {
            // Some model do not support function calling with tool specifications
            context.effectiveStreamingChatModel(methodCreateInfo, methodArgs).chat(chatRequest, handler);
        } catch (Exception e) {
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
        }
    }

    /**
     * Returns the StreamingHandle that can be used to cancel the underlying stream.
     * <p>
     * Returns {@code null} if streaming has not started yet, or a {@link NoopStreamingHandle}
     * if the handler has not received any partial responses.
     *
     * @apiNote This uses langchain4j's experimental StreamingHandle API (since 1.8.0)
     */
    public StreamingHandle getStreamingHandle() {
        return handler != null ? handler.getStreamingHandle() : null;
    }

    private void validateConfiguration() {
        if (onPartialResponseInvoked != 1) {
            throw new IllegalConfigurationException("One of [onPartialResponse, onNext] " +
                    "must be invoked on TokenStream exactly 1 time");
        }

        if (onCompleteResponseInvoked > 1) {
            throw new IllegalConfigurationException("One of [onCompleteResponse, onComplete] " +
                    "can be invoked on TokenStream at most 1 time");
        }

        if (onRetrievedInvoked > 1) {
            throw new IllegalConfigurationException("onRetrieved can be invoked on TokenStream at most 1 time");
        }

        if (toolExecuteInvoked > 1) {
            throw new IllegalConfigurationException("onToolExecuted can be invoked on TokenStream at most 1 time");
        }

        if (beforeToolExecutionInvoked > 1) {
            throw new IllegalConfigurationException("beforeToolExecution can be invoked on TokenStream at most 1 time");
        }

        if (onPartialThinkingInvoked > 1) {
            throw new IllegalConfigurationException("onPartialThinking can be invoked on TokenStream at most 1 time");
        }

        if (onIntermediateResponseInvoked > 1) {
            throw new IllegalConfigurationException("onIntermediateResponse can be invoked on TokenStream at most 1 time");
        }

        if (onErrorInvoked + ignoreErrorsInvoked != 1) {
            throw new IllegalConfigurationException("One of [onError, ignoreErrors] " +
                    "must be invoked on TokenStream exactly 1 time");
        }
    }

    private List<ChatMessage> initTemporaryMemory(AiServiceContext context, List<ChatMessage> messagesToSend) {
        if (context.hasChatMemory()) {
            return emptyList();
        } else {
            return new ArrayList<>(messagesToSend);
        }
    }
}
