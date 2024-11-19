package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
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
import dev.langchain4j.exception.IllegalConfigurationException;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.TokenStream;
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
    private final AiServiceContext context;
    private final Object memoryId;
    private final Context cxtx;
    private final boolean mustSwitchToWorkerThread;

    private Consumer<String> tokenHandler;
    private Consumer<List<Content>> contentsHandler;
    private Consumer<Throwable> errorHandler;
    private Consumer<Response<AiMessage>> completionHandler;

    private int onNextInvoked;
    private int onCompleteInvoked;
    private int onRetrievedInvoked;
    private int onErrorInvoked;
    private int ignoreErrorsInvoked;

    public QuarkusAiServiceTokenStream(List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            List<Content> retrievedContents,
            AiServiceContext context,
            Object memoryId, Context ctxt, boolean mustSwitchToWorkerThread) {
        this.messages = ensureNotEmpty(messages, "messages");
        this.toolSpecifications = copyIfNotNull(toolSpecifications);
        this.toolExecutors = copyIfNotNull(toolExecutors);
        this.retrievedContents = retrievedContents;
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");
        ensureNotNull(context.streamingChatModel, "streamingChatModel");
        this.cxtx = ctxt; // If set, it means we need to handle the context propagation.
        this.mustSwitchToWorkerThread = mustSwitchToWorkerThread; // If true, we need to switch to a worker thread to execute tools.
    }

    @Override
    public TokenStream onNext(Consumer<String> tokenHandler) {
        this.tokenHandler = tokenHandler;
        this.onNextInvoked++;
        return this;
    }

    @Override
    public TokenStream onRetrieved(Consumer<List<Content>> contentsHandler) {
        this.contentsHandler = contentsHandler;
        this.onRetrievedInvoked++;
        return this;
    }

    @Override
    public TokenStream onComplete(Consumer<Response<AiMessage>> completionHandler) {
        this.completionHandler = completionHandler;
        this.onCompleteInvoked++;
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
        QuarkusAiServiceStreamingResponseHandler handler = new QuarkusAiServiceStreamingResponseHandler(
                context,
                memoryId,
                tokenHandler,
                completionHandler,
                errorHandler,
                initTemporaryMemory(context, messages),
                new TokenUsage(),
                toolSpecifications,
                toolExecutors,
                mustSwitchToWorkerThread,
                cxtx);

        if (contentsHandler != null && retrievedContents != null) {
            contentsHandler.accept(retrievedContents);
        }

        if (isNullOrEmpty(toolSpecifications)) {
            context.streamingChatModel.generate(messages, handler);
        } else {
            try {
                // Some model do not support function calling with tool specifications
                context.streamingChatModel.generate(messages, toolSpecifications, handler);
            } catch (Exception e) {
                if (errorHandler != null) {
                    errorHandler.accept(e);
                }
            }
        }
    }

    private void validateConfiguration() {
        if (onNextInvoked != 1) {
            throw new IllegalConfigurationException("onNext must be invoked exactly 1 time");
        }

        if (onCompleteInvoked > 1) {
            throw new IllegalConfigurationException("onComplete must be invoked at most 1 time");
        }

        if (onRetrievedInvoked > 1) {
            throw new IllegalConfigurationException("onRetrieved must be invoked at most 1 time");
        }

        if (onErrorInvoked + ignoreErrorsInvoked != 1) {
            throw new IllegalConfigurationException("One of onError or ignoreErrors must be invoked exactly 1 time");
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
