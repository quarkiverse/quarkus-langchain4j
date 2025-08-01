package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutor;
import io.quarkiverse.langchain4j.response.ResponseAugmenterParams;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.AbstractMulti;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.vertx.core.Context;

class TokenStreamMulti extends AbstractMulti<String> implements Multi<String> {
    private final List<ChatMessage> messagesToSend;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolsExecutors;
    private final List<Content> contents;
    private final QuarkusAiServiceContext context;
    private final Object memoryId;
    private final boolean switchToWorkerThreadForToolExecution;
    private final boolean isCallerRunningOnWorkerThread;

    TokenStreamMulti(List<ChatMessage> messagesToSend, List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            List<Content> contents, QuarkusAiServiceContext context, Object memoryId,
            boolean switchToWorkerThreadForToolExecution, boolean isCallerRunningOnWorkerThread) {
        // We need to pass and store the parameters to the constructor because we need to re-create a stream on every subscription.
        this.messagesToSend = messagesToSend;
        this.toolSpecifications = toolSpecifications;
        this.toolsExecutors = toolExecutors;
        this.contents = contents;
        this.context = context;
        this.memoryId = memoryId;
        this.switchToWorkerThreadForToolExecution = switchToWorkerThreadForToolExecution;
        this.isCallerRunningOnWorkerThread = isCallerRunningOnWorkerThread;
    }

    @Override
    public void subscribe(MultiSubscriber<? super String> subscriber) {
        UnicastProcessor<String> processor = UnicastProcessor.create();
        processor.subscribe(subscriber);

        createTokenStream(processor);
    }

    private void createTokenStream(UnicastProcessor<String> processor) {
        Context ctxt = null;
        if (switchToWorkerThreadForToolExecution || isCallerRunningOnWorkerThread) {
            // we create or retrieve the current context, to use `executeBlocking` when required.
            ctxt = VertxContext.getOrCreateDuplicatedContext();
        }

        var stream = new QuarkusAiServiceTokenStream(messagesToSend, toolSpecifications,
                toolsExecutors, contents, context, memoryId, ctxt, switchToWorkerThreadForToolExecution,
                isCallerRunningOnWorkerThread);
        TokenStream tokenStream = stream
                .onPartialResponse(processor::onNext)
                .onCompleteResponse(message -> processor.onComplete())
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

    static final class TokenStreamMultiResponseAugmentorSupport<R> implements Function<Multi<String>, Multi<R>> {
        private final AiServiceMethodCreateInfo methodCreateInfo;
        private final UserMessage userMessage;
        private final ChatMemory chatMemory;
        private final AugmentationResult actualAugmentationResult;
        private final Map<String, Object> templateVariables;

        TokenStreamMultiResponseAugmentorSupport(AiServiceMethodCreateInfo methodCreateInfo, UserMessage userMessage,
                ChatMemory chatMemory, AugmentationResult actualAugmentationResult, Map<String, Object> templateVariables) {
            this.methodCreateInfo = methodCreateInfo;
            this.userMessage = userMessage;
            this.chatMemory = chatMemory;
            this.actualAugmentationResult = actualAugmentationResult;
            this.templateVariables = templateVariables;
        }

        public Multi<R> apply(Multi<String> m) {
            return (Multi<R>) ResponseAugmenterSupport.apply(m, methodCreateInfo,
                    new ResponseAugmenterParams(userMessage,
                            chatMemory, actualAugmentationResult, methodCreateInfo.getUserMessageTemplate(),
                            Collections.unmodifiableMap(templateVariables)));
        }
    }
}
