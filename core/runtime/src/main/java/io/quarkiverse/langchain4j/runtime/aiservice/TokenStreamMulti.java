package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutor;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.AbstractMulti;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.vertx.core.Context;

class TokenStreamMulti extends AbstractMulti<ChatEvent> implements Multi<ChatEvent> {
    private final List<ChatMessage> messagesToSend;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolsExecutors;
    private final List<Content> contents;
    private final QuarkusAiServiceContext context;
    private final InvocationContext invocationContext;
    private final Object memoryId;
    private final boolean switchToWorkerThreadForToolExecution;
    private final boolean isCallerRunningOnWorkerThread;

    TokenStreamMulti(List<ChatMessage> messagesToSend, List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            List<Content> contents, QuarkusAiServiceContext context, InvocationContext invocationContext, Object memoryId,
            boolean switchToWorkerThreadForToolExecution, boolean isCallerRunningOnWorkerThread) {
        // We need to pass and store the parameters to the constructor because we need to re-create a stream on every subscription.
        this.messagesToSend = messagesToSend;
        this.toolSpecifications = toolSpecifications;
        this.toolsExecutors = toolExecutors;
        this.contents = contents;
        this.context = context;
        this.invocationContext = invocationContext;
        this.memoryId = memoryId;
        this.switchToWorkerThreadForToolExecution = switchToWorkerThreadForToolExecution;
        this.isCallerRunningOnWorkerThread = isCallerRunningOnWorkerThread;
    }

    @Override
    public void subscribe(MultiSubscriber<? super ChatEvent> subscriber) {
        UnicastProcessor<ChatEvent> processor = UnicastProcessor.create();
        processor.subscribe(subscriber);

        createTokenStream(processor);
    }

    private void createTokenStream(UnicastProcessor<ChatEvent> processor) {
        Context ctxt = null;
        if (switchToWorkerThreadForToolExecution || isCallerRunningOnWorkerThread) {
            // we create or retrieve the current context, to use `executeBlocking` when required.
            ctxt = VertxContext.getOrCreateDuplicatedContext();
        }

        var stream = new QuarkusAiServiceTokenStream(messagesToSend, toolSpecifications,
                toolsExecutors, contents, context, invocationContext, memoryId, ctxt, switchToWorkerThreadForToolExecution,
                isCallerRunningOnWorkerThread);
        TokenStream tokenStream = stream
                .onPartialResponse(chunk -> processor.onNext(new ChatEvent.PartialResponseEvent(chunk)))
                .onPartialThinking(thinking -> processor.onNext(new ChatEvent.PartialThinkingEvent(thinking.text())))
                .onCompleteResponse(message -> {
                    processor.onNext(new ChatEvent.ChatCompletedEvent(message));
                    processor.onComplete();
                })
                .onRetrieved(content -> processor.onNext(new ChatEvent.ContentFetchedEvent(content)))
                .onIntermediateResponse(response -> processor.onNext(new ChatEvent.IntermediateResponseEvent(response)))
                .beforeToolExecution(
                        beforeExecution -> processor.onNext(new ChatEvent.BeforeToolExecutionEvent(beforeExecution.request())))
                .onToolExecuted(execution -> processor.onNext(new ChatEvent.ToolExecutedEvent(execution)))
                .onError(processor::onError);
        // This is equivalent to "run subscription on worker thread"
        if (switchToWorkerThreadForToolExecution && Context.isOnEventLoopThread()) {
            ctxt.executeBlocking(new Callable<Void>() {
                @Override
                public Void call() {
                    tokenStream.start();
                    return null;
                }
            });
        } else {
            tokenStream.start();
        }
    }
}
