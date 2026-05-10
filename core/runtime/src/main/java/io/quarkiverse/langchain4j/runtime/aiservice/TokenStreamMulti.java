package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.AiServiceTokenStream;
import dev.langchain4j.service.AiServiceTokenStreamParameters;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolServiceContext;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.AbstractMulti;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.vertx.core.Context;

/**
 * Adapts the upstream {@link AiServiceTokenStream} to a {@code Multi<ChatEvent>}.
 * <p>
 * The streaming tool loop is owned by upstream — this class only translates upstream callbacks to
 * Mutiny events, and bridges Multi subscription cancellation to the provider's
 * {@link StreamingHandle} captured via the *WithContext callbacks. Vert.x context propagation for
 * tool dispatch is handled by the executor wrap (see {@link VertxContextAwareExecutor}). When the
 * caller is on the event loop and tools may block, we still trigger the initial subscription on a
 * worker via {@code Context.executeBlocking} so the LLM call doesn't run on the event loop.
 */
class TokenStreamMulti extends AbstractMulti<ChatEvent> implements Multi<ChatEvent> {

    private final List<ChatMessage> messagesToSend;
    private final ToolServiceContext toolServiceContext;
    private final List<Content> contents;
    private final QuarkusAiServiceContext context;
    private final InvocationContext invocationContext;
    private final boolean switchToWorkerThreadForToolExecution;
    private final boolean isCallerRunningOnWorkerThread;
    private final GuardrailRequestParams commonGuardrailParams;
    private final StreamingChatModel streamingChatModel;

    TokenStreamMulti(List<ChatMessage> messagesToSend, ToolServiceContext toolServiceContext, List<Content> contents,
            QuarkusAiServiceContext context, InvocationContext invocationContext,
            boolean switchToWorkerThreadForToolExecution, boolean isCallerRunningOnWorkerThread,
            GuardrailRequestParams commonGuardrailParams, StreamingChatModel streamingChatModel) {
        // We need to pass and store the parameters to the constructor because we need to re-create a stream on every subscription.
        this.messagesToSend = messagesToSend;
        this.toolServiceContext = toolServiceContext;
        this.contents = contents;
        this.context = context;
        this.invocationContext = invocationContext;
        this.switchToWorkerThreadForToolExecution = switchToWorkerThreadForToolExecution;
        this.isCallerRunningOnWorkerThread = isCallerRunningOnWorkerThread;
        this.commonGuardrailParams = commonGuardrailParams;
        this.streamingChatModel = streamingChatModel;
    }

    @Override
    public void subscribe(MultiSubscriber<? super ChatEvent> subscriber) {
        UnicastProcessor<ChatEvent> processor = UnicastProcessor.create();

        // Capture once, used for both stream creation and worker-thread dispatch.
        Context vertxContext = null;
        if (switchToWorkerThreadForToolExecution || isCallerRunningOnWorkerThread) {
            vertxContext = VertxContext.getOrCreateDuplicatedContext();
        }

        // Captured lazily from the first *WithContext callback. Until populated, cancellation is a
        // no-op (if cancellation arrives before any partial response, we let in-flight tool
        // dispatch finish on its own).
        AtomicReference<StreamingHandle> handleRef = new AtomicReference<>();

        AiServiceTokenStream stream = createTokenStream(processor, handleRef);

        // Bridge Multi cancellation to upstream stream cancellation.
        Multi<ChatEvent> cancellableMulti = processor.onCancellation().invoke(() -> {
            StreamingHandle handle = handleRef.get();
            if (handle != null) {
                handle.cancel();
            }
        });

        cancellableMulti.subscribe(subscriber);

        try {
            startTokenStream(stream, vertxContext, processor);
        } catch (Throwable t) {
            // Upstream's AiServiceTokenStream.start() does not wrap the synchronous
            // streamingChatModel.chat(...) call in a try/catch — providers that fail synchronously
            // (e.g. UnsupportedFeatureException from a model that doesn't support tools) would
            // otherwise escape the subscriber. Forward the failure through the processor instead.
            processor.onError(t);
        }
    }

    private AiServiceTokenStream createTokenStream(UnicastProcessor<ChatEvent> processor,
            AtomicReference<StreamingHandle> handleRef) {
        // methodKey is intentionally NOT passed to upstream for the Multi path. Upstream's streaming
        // handler buffers partial responses when guardrailService.hasOutputGuardrails(methodKey) is
        // true and replays them only after guardrail validation. Quarkus owns Multi-path output
        // guardrails through OutputGuardrailStreamingMapper (downstream of this Multi), which
        // requires partial responses to flow through unbuffered. Passing null here keeps the
        // partials flowing; the Quarkus guardrail mapper does the buffering / validation itself.
        AiServiceTokenStreamParameters params = AiServiceTokenStreamParameters.builder()
                .messages(messagesToSend)
                .toolServiceContext(toolServiceContext)
                .toolExecutor(context.parallelToolExecutor)
                .retrievedContents(contents)
                .context(context)
                .streamingChatModel(streamingChatModel)
                .invocationContext(invocationContext)
                .methodKey(null)
                .toolArgumentsErrorHandler((e, c) -> {
                    throw new RuntimeException(e);
                })
                .toolExecutionErrorHandler((e, c) -> ToolErrorHandlerResult.text(e.getMessage()))
                .commonGuardrailParams(commonGuardrailParams)
                .build();

        AiServiceTokenStream stream = new AiServiceTokenStream(params);
        // Use the *WithContext variants where available so the provider's StreamingHandle can be
        // captured for cancellation. The non-WithContext callbacks below cover events that don't
        // expose a streaming handle (the provider has either already given us one via partial-text
        // or it doesn't support cancellation at all).
        stream
                .onPartialResponseWithContext((partial, ctx) -> {
                    handleRef.compareAndSet(null, ctx.streamingHandle());
                    processor.onNext(new ChatEvent.PartialResponseEvent(partial.text()));
                })
                .onPartialThinkingWithContext((thinking, ctx) -> {
                    handleRef.compareAndSet(null, ctx.streamingHandle());
                    processor.onNext(new ChatEvent.PartialThinkingEvent(thinking.text()));
                })
                .onPartialToolCallWithContext((toolCall, ctx) -> {
                    handleRef.compareAndSet(null, ctx.streamingHandle());
                    processor.onNext(new ChatEvent.PartialToolCallEvent(toolCall));
                })
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
        return stream;
    }

    private void startTokenStream(AiServiceTokenStream stream, Context vertxContext, UnicastProcessor<ChatEvent> processor) {
        // Avoid running blocking-tools-capable code on the event loop. The upstream stream issues
        // the first chat request synchronously inside start(); subsequent tool dispatch goes through
        // the executor wrap, so this hop is only needed to keep the initial request off the EL.
        if (switchToWorkerThreadForToolExecution && Context.isOnEventLoopThread()) {
            vertxContext.executeBlocking(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        stream.start();
                    } catch (Throwable t) {
                        // Same forwarding as the inline path — upstream's start() doesn't catch
                        // synchronous chat() failures, so keep them off the event loop and route
                        // them to the subscriber via the processor.
                        processor.onError(t);
                    }
                    return null;
                }
            });
        } else {
            stream.start();
        }
    }
}
