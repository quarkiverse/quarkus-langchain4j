package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Objects.nonNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import jakarta.enterprise.inject.spi.CDI;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import io.quarkiverse.langchain4j.runtime.PreventsErrorHandlerExecution;
import io.quarkiverse.langchain4j.runtime.ToolCallsLimitExceededException;
import io.quarkiverse.langchain4j.runtime.VirtualThreadSupport;
import io.quarkiverse.langchain4j.runtime.config.ToolsConfig;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.vertx.core.Context;

/**
 * A {@link StreamingResponseHandler} implementation for Quarkus. The main difference with the upstream implementation is the
 * thread switch when
 * receiving the `completion` event when there is tool execution requests.
 */
public class QuarkusAiServiceStreamingResponseHandler implements StreamingChatResponseHandler {

    private final Logger log = Logger.getLogger(QuarkusAiServiceStreamingResponseHandler.class);

    private final ChatRequest chatRequest;
    private final QuarkusAiServiceContext context;
    private final InvocationContext invocationContext;
    private final Object memoryId;

    private final Consumer<String> partialResponseHandler;
    private final Consumer<PartialThinking> partialThinkingHandler;
    private final Consumer<PartialToolCall> partialToolCallHandler;
    private final Consumer<Response<AiMessage>> completionHandler;
    private final Consumer<BeforeToolExecution> beforeToolExecutionHandler;
    private final Consumer<ChatResponse> intermediateResponseHandler;
    private final Consumer<ToolExecution> toolExecuteHandler;
    private final Consumer<ChatResponse> completeResponseHandler;
    private final Consumer<Throwable> errorHandler;

    private final List<ChatMessage> temporaryMemory;
    private final TokenUsage tokenUsage;

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final Context executionContext;
    private final boolean mustSwitchToWorkerThread;
    private final boolean switchToWorkerForEmission;
    private final AiServiceMethodCreateInfo methodCreateInfo;
    private final Object[] methodArgs;
    private final ExecutorService executor;
    private final AtomicBoolean cancelled;
    private volatile StreamingHandle streamingHandle = NoopStreamingHandle.INSTANCE;

    QuarkusAiServiceStreamingResponseHandler(ChatRequest chatRequest, QuarkusAiServiceContext context,
            InvocationContext invocationContext,
            Object memoryId,
            Consumer<String> partialResponseHandler,
            Consumer<PartialThinking> partialThinkingHandler,
            Consumer<PartialToolCall> partialToolCallHandler,
            Consumer<BeforeToolExecution> beforeToolExecutionHandler,
            Consumer<ChatResponse> intermediateResponseHandler,
            Consumer<ToolExecution> toolExecuteHandler,
            Consumer<ChatResponse> completeResponseHandler,
            Consumer<Response<AiMessage>> completionHandler,
            Consumer<Throwable> errorHandler,
            List<ChatMessage> temporaryMemory,
            TokenUsage tokenUsage,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            boolean mustSwitchToWorkerThread,
            boolean switchToWorkerForEmission,
            Context cxtx,
            AiServiceMethodCreateInfo methodCreateInfo,
            Object[] methodArgs,
            AtomicBoolean cancelled) {
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.context = ensureNotNull(context, "context");
        this.invocationContext = ensureNotNull(invocationContext, "invocationContext");
        this.memoryId = ensureNotNull(memoryId, "memoryId");

        this.partialResponseHandler = ensureNotNull(partialResponseHandler, "partialResponseHandler");
        this.partialThinkingHandler = partialThinkingHandler;
        this.partialToolCallHandler = partialToolCallHandler;
        this.beforeToolExecutionHandler = beforeToolExecutionHandler;
        this.intermediateResponseHandler = intermediateResponseHandler;
        this.completeResponseHandler = completeResponseHandler;
        this.completionHandler = completionHandler;
        this.toolExecuteHandler = toolExecuteHandler;
        this.errorHandler = errorHandler;

        this.temporaryMemory = new ArrayList<>(temporaryMemory);
        this.tokenUsage = ensureNotNull(tokenUsage, "tokenUsage");

        this.toolSpecifications = copyIfNotNull(toolSpecifications);
        this.toolExecutors = copyIfNotNull(toolExecutors);

        this.mustSwitchToWorkerThread = mustSwitchToWorkerThread;
        this.executionContext = cxtx;
        this.switchToWorkerForEmission = switchToWorkerForEmission;
        this.methodCreateInfo = methodCreateInfo;
        this.methodArgs = methodArgs;
        this.cancelled = cancelled;
        if (executionContext == null) {
            // We do not have a context, but we still need to make sure we are not blocking the event loop and ordered
            // is respected.
            executor = Executors.newSingleThreadExecutor();
        } else {
            executor = null;
        }
    }

    public QuarkusAiServiceStreamingResponseHandler(ChatRequest chatRequest, QuarkusAiServiceContext context,
            InvocationContext invocationContext, Object memoryId,
            Consumer<String> partialResponseHandler,
            Consumer<PartialThinking> partialThinkingHandler,
            Consumer<PartialToolCall> partialToolCallHandler,
            Consumer<BeforeToolExecution> beforeToolExecutionHandler,
            Consumer<ChatResponse> intermediateResponseHandler,
            Consumer<ToolExecution> toolExecuteHandler, Consumer<ChatResponse> completeResponseHandler,
            Consumer<Response<AiMessage>> completionHandler,
            Consumer<Throwable> errorHandler, List<ChatMessage> temporaryMemory, TokenUsage sum,
            List<ToolSpecification> toolSpecifications, Map<String, ToolExecutor> toolExecutors,
            boolean mustSwitchToWorkerThread, boolean switchToWorkerForEmission, Context executionContext,
            ExecutorService executor, AiServiceMethodCreateInfo methodCreateInfo, Object[] methodArgs,
            AtomicBoolean cancelled) {
        this.chatRequest = ensureNotNull(chatRequest, "chatRequest");
        this.context = context;
        this.invocationContext = ensureNotNull(invocationContext, "invocationContext");
        this.memoryId = memoryId;
        this.partialResponseHandler = ensureNotNull(partialResponseHandler, "partialResponseHandler");
        this.partialThinkingHandler = partialThinkingHandler;
        this.partialToolCallHandler = partialToolCallHandler;
        this.beforeToolExecutionHandler = beforeToolExecutionHandler;
        this.intermediateResponseHandler = intermediateResponseHandler;
        this.toolExecuteHandler = toolExecuteHandler;
        this.completeResponseHandler = completeResponseHandler;
        this.completionHandler = completionHandler;
        this.errorHandler = errorHandler;
        this.temporaryMemory = temporaryMemory;
        this.tokenUsage = sum;
        this.toolSpecifications = toolSpecifications;
        this.toolExecutors = toolExecutors;
        this.mustSwitchToWorkerThread = mustSwitchToWorkerThread;
        this.switchToWorkerForEmission = switchToWorkerForEmission;
        this.executionContext = executionContext;
        this.executor = executor;
        this.methodCreateInfo = methodCreateInfo;
        this.methodArgs = methodArgs;
        this.cancelled = cancelled;
    }

    private <T> void fireInvocationComplete(T result) {
        context.eventListenerRegistrar.fireEvent(AiServiceCompletedEvent.builder()
                .invocationContext(invocationContext)
                .result(result)
                .build());
    }

    private void fireRequestIssuedEvent(ChatRequest chatRequest) {
        context.eventListenerRegistrar.fireEvent(AiServiceRequestIssuedEvent.builder()
                .invocationContext(invocationContext)
                .request(chatRequest)
                .build());
    }

    private void fireToolExecutedEvent(ToolExecutionRequest request, String result) {
        context.eventListenerRegistrar.fireEvent(ToolExecutedEvent.builder()
                .invocationContext(invocationContext)
                .request(request)
                .resultText(result)
                .build());
    }

    private void fireResponseReceivedEvent(ChatResponse chatResponse) {
        context.eventListenerRegistrar.fireEvent(AiServiceResponseReceivedEvent.builder()
                .invocationContext(invocationContext)
                .request(chatRequest)
                .response(chatResponse)
                .build());
    }

    private void fireErrorReceived(Throwable error) {
        context.eventListenerRegistrar.fireEvent(AiServiceErrorEvent.builder()
                .invocationContext(invocationContext)
                .error(error)
                .build());
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        if (isCancelled()) {
            streamingHandle.cancel();
            return;
        }
        execute(new Runnable() {
            @Override
            public void run() {
                partialResponseHandler.accept(partialResponse);
            }
        });

    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking) {
        if (isCancelled()) {
            streamingHandle.cancel();
            return;
        }
        if (partialThinkingHandler != null) {
            execute(new Runnable() {
                @Override
                public void run() {
                    partialThinkingHandler.accept(partialThinking);
                }
            });
        }
    }

    @Override
    public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
        captureStreamingHandle(context.streamingHandle());
        onPartialResponse(partialResponse.text());
    }

    @Override
    public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
        captureStreamingHandle(context.streamingHandle());
        onPartialThinking(partialThinking);
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall) {
        if (isCancelled()) {
            streamingHandle.cancel();
            return;
        }
        if (partialToolCallHandler != null) {
            execute(new Runnable() {
                @Override
                public void run() {
                    partialToolCallHandler.accept(partialToolCall);
                }
            });
        }
    }

    @Override
    public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
        captureStreamingHandle(context.streamingHandle());
        onPartialToolCall(partialToolCall);
    }

    private void captureStreamingHandle(StreamingHandle handle) {
        if (this.streamingHandle == NoopStreamingHandle.INSTANCE) {
            this.streamingHandle = handle;
        }
    }

    /**
     * Returns the StreamingHandle that can be used to cancel the underlying stream.
     * <p>
     * Returns {@link NoopStreamingHandle} until the first partial response is received
     * from a provider that supports cancellation.
     *
     * @apiNote This uses langchain4j's experimental StreamingHandle API (since 1.8.0)
     */
    public StreamingHandle getStreamingHandle() {
        return streamingHandle;
    }

    private void executeTools(Runnable runnable, boolean dispatchToVirtualThread, boolean parallelBatch,
            ToolsDispatcher dispatcher) {
        Runnable safeRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Exception e) {
                    onError(e);
                }
            }
        };

        if (dispatchToVirtualThread) {
            // In parallel mode the per-tool tasks acquire their own permits, so the carrier
            // does not hold a batch permit (otherwise max-concurrent would be double-counted
            // and admit fewer tools than configured).
            executeWithBatchVirtualThreadDispatch(safeRunnable, dispatcher, !parallelBatch);
        } else if (mustSwitchToWorkerThread && Context.isOnEventLoopThread()) {
            executeOnWorkerThread(safeRunnable, false);
        } else {
            safeRunnable.run();
        }
    }

    private static ToolsDispatcher toolsDispatcher() {
        try {
            return CDI.current().select(ToolsDispatcher.class).get();
        } catch (RuntimeException ignored) {
            // CDI not available (e.g. bootstrap paths where the container is not yet up);
            // fall back to defaults.
            return ToolsDispatcher.DEFAULT;
        }
    }

    private boolean shouldDispatchToVirtualThread(BatchDispatchDecision decision, ToolsDispatcher dispatcher) {
        switch (dispatcher.dispatchMode()) {
            case LEGACY:
                return false;
            case AUTO:
            default:
                switch (decision.mode()) {
                    case ALL_VIRTUAL_THREAD:
                        return true;
                    case MIXED:
                        logMixedBatch(dispatcher.mixedBatchLogLevel(), decision);
                        return false;
                    case NONE_VIRTUAL_THREAD:
                    default:
                        return false;
                }
        }
    }

    private BatchDispatchDecision determineBatchDispatchDecision(List<ToolExecutionRequest> toolExecutionRequests) {
        if (toolExecutionRequests == null || toolExecutionRequests.isEmpty()) {
            return new BatchDispatchDecision(BatchExecutionMode.NONE_VIRTUAL_THREAD, List.of(), List.of());
        }
        boolean anyVirtual = false;
        boolean anyOther = false;
        LinkedHashSet<String> requestedToolNames = new LinkedHashSet<>();
        LinkedHashSet<String> nonVirtualThreadToolNames = new LinkedHashSet<>();
        for (ToolExecutionRequest request : toolExecutionRequests) {
            String toolName = request.name();
            requestedToolNames.add(toolName);
            ToolExecutor toolExecutor = toolExecutors.get(toolName);
            if (toolExecutor instanceof QuarkusToolExecutor quarkusToolExecutor
                    && quarkusToolExecutor.getMethodCreateInfo() != null
                    && quarkusToolExecutor.getMethodCreateInfo()
                            .executionModel() == ToolMethodCreateInfo.ExecutionModel.VIRTUAL_THREAD) {
                anyVirtual = true;
            } else {
                // Unknown executors (e.g. MCP) are treated as non-virtual-thread.
                anyOther = true;
                nonVirtualThreadToolNames.add(toolName);
            }
        }
        if (anyVirtual && anyOther) {
            return new BatchDispatchDecision(BatchExecutionMode.MIXED, List.copyOf(requestedToolNames),
                    List.copyOf(nonVirtualThreadToolNames));
        }
        if (anyVirtual) {
            return new BatchDispatchDecision(BatchExecutionMode.ALL_VIRTUAL_THREAD, List.copyOf(requestedToolNames),
                    List.of());
        }
        return new BatchDispatchDecision(BatchExecutionMode.NONE_VIRTUAL_THREAD, List.copyOf(requestedToolNames),
                List.copyOf(nonVirtualThreadToolNames));
    }

    private void logMixedBatch(ToolsConfig.MixedBatchLogLevel level, BatchDispatchDecision decision) {
        String message = "Mixed tool execution models detected for AI service method " + aiServiceMethodDescription()
                + ". Requested tools: " + decision.requestedToolNames()
                + ". Non-VIRTUAL_THREAD tools: " + decision.nonVirtualThreadToolNames()
                + ". Skipping the full-batch virtual-thread dispatch optimization and using legacy per-tool scheduling "
                + "instead. The optimization only applies when every requested tool resolves to the VIRTUAL_THREAD "
                + "execution model. Set quarkus.langchain4j.tools.mixed-batch-log-level=off to silence this warning.";
        switch (level) {
            case WARN:
                log.warn(message);
                break;
            case INFO:
                log.info(message);
                break;
            case DEBUG:
                log.debug(message);
                break;
            case OFF:
            default:
                // no-op
                break;
        }
    }

    private String aiServiceMethodDescription() {
        if (methodCreateInfo == null) {
            return "<unknown>";
        }
        return simpleTypeName(methodCreateInfo.getInterfaceName()) + "#" + methodCreateInfo.getMethodName();
    }

    private String simpleTypeName(String typeName) {
        int packageSeparator = typeName.lastIndexOf('.');
        String simpleName = packageSeparator >= 0 ? typeName.substring(packageSeparator + 1) : typeName;
        int nestedSeparator = simpleName.lastIndexOf('$');
        return nestedSeparator >= 0 ? simpleName.substring(nestedSeparator + 1) : simpleName;
    }

    private void executeWithBatchVirtualThreadDispatch(Runnable runnable, ToolsDispatcher dispatcher,
            boolean acquireBatchPermit) {
        Runnable guardedRunnable = new Runnable() {
            @Override
            public void run() {
                Semaphore semaphore = acquireBatchPermit ? dispatcher.semaphore() : null;
                boolean acquired = false;
                try {
                    if (semaphore != null) {
                        semaphore.acquire();
                        acquired = true;
                    }
                    runnable.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    onError(e);
                } catch (Throwable t) {
                    // Defensive: safeRunnable already routes Exception to onError, but catch
                    // Throwable here so nothing escapes into the virtual-thread executor's
                    // uncaught-exception handler silently.
                    onError(t);
                } finally {
                    if (acquired) {
                        semaphore.release();
                    }
                }
            }
        };

        if (VirtualThreadSupport.isCurrentThreadVirtual()) {
            // Already on a virtual thread, run inline but still apply the concurrency cap.
            guardedRunnable.run();
        } else {
            executeOnVirtualThread(guardedRunnable);
        }
    }

    private void executeOnVirtualThread(Runnable runnable) {
        VirtualThreadsRecorder.getCurrent().execute(runnable);
    }

    private enum BatchExecutionMode {
        ALL_VIRTUAL_THREAD,
        NONE_VIRTUAL_THREAD,
        MIXED
    }

    private static final class BatchDispatchDecision {
        private final BatchExecutionMode mode;
        private final List<String> requestedToolNames;
        private final List<String> nonVirtualThreadToolNames;

        private BatchDispatchDecision(BatchExecutionMode mode, List<String> requestedToolNames,
                List<String> nonVirtualThreadToolNames) {
            this.mode = mode;
            this.requestedToolNames = requestedToolNames;
            this.nonVirtualThreadToolNames = nonVirtualThreadToolNames;
        }

        private BatchExecutionMode mode() {
            return mode;
        }

        private List<String> requestedToolNames() {
            return requestedToolNames;
        }

        private List<String> nonVirtualThreadToolNames() {
            return nonVirtualThreadToolNames;
        }
    }

    private void execute(Runnable runnable) {
        if (switchToWorkerForEmission && Context.isOnEventLoopThread()) {
            executeOnWorkerThread(runnable, true);
        } else {
            runnable.run();
        }
    }

    private void executeOnWorkerThread(Runnable runnable, boolean ordered) {
        if (executionContext != null) {
            executionContext.executeBlocking(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    runnable.run();
                    return null;
                }
            }, ordered);
        } else {
            executor.submit(runnable);
        }
    }

    /**
     * Executes a tool and handles any exceptions using the configured error handlers.
     * <p>
     * If a {@link ToolArgumentsException} occurs and {@code toolArgumentsErrorHandler} is configured,
     * the handler is invoked and an error result is returned to the LLM instead of propagating the exception.
     * <p>
     * For other exceptions, if {@code toolExecutionErrorHandler} is configured (and the exception does not
     * implement {@link PreventsErrorHandlerExecution}), the handler is invoked and an error result is returned.
     * <p>
     * This allows the LLM to recover from tool failures by receiving error feedback and potentially retrying.
     *
     * @param toolExecutionRequest the tool execution request from the LLM
     * @param toolExecutor the executor for the tool
     * @param invocationContext the current invocation context
     * @param toolArgumentsErrorHandler optional handler for argument parsing errors
     * @param toolExecutionErrorHandler optional handler for execution errors
     * @return the tool execution result, which may indicate an error if handled by an error handler
     */
    private ToolExecutionResult executeTool(ToolExecutionRequest toolExecutionRequest, ToolExecutor toolExecutor,
            InvocationContext invocationContext,
            ToolArgumentsErrorHandler toolArgumentsErrorHandler,
            ToolExecutionErrorHandler toolExecutionErrorHandler) {
        ToolExecutionResult toolExecutionResult;
        try {
            toolExecutionResult = toolExecutor.executeWithContext(toolExecutionRequest, invocationContext);
        } catch (ToolArgumentsException e) {
            if (toolArgumentsErrorHandler != null) {
                log.debugv(e, "Error occurred while executing tool arguments. Executing  ",
                        toolArgumentsErrorHandler.getClass().getName() + "' to handle it");
                ToolErrorContext errorContext = ToolErrorContext.builder()
                        .toolExecutionRequest(toolExecutionRequest)
                        .invocationContext(invocationContext)
                        .build();
                ToolErrorHandlerResult toolErrorHandlerResult = toolArgumentsErrorHandler.handle(e, errorContext);
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText(toolErrorHandlerResult.text())
                        .build();
            } else {
                throw e;
            }
        } catch (Exception e) {
            if (e instanceof PreventsErrorHandlerExecution) {
                // preserve semantics for existing code
                throw e;
            }
            if (toolExecutionErrorHandler != null) {
                log.debugv(e, "Error occurred while executing tool. Executing '",
                        toolExecutionErrorHandler.getClass().getName() + "' to handle it");
                ToolErrorContext errorContext = ToolErrorContext.builder()
                        .toolExecutionRequest(toolExecutionRequest)
                        .invocationContext(invocationContext)
                        .build();
                ToolErrorHandlerResult toolErrorHandlerResult = toolExecutionErrorHandler.handle(e, errorContext);
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText(toolErrorHandlerResult.text())
                        .build();
            } else {
                throw e;
            }
        }
        log.debugv("Result of {0} is '{1}'", toolExecutionRequest, toolExecutionResult);

        return toolExecutionResult;
    }

    private void runParallelToolBatch(AiMessage aiMessage, ToolsDispatcher dispatcher) {
        List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();
        int n = requests.size();

        // Fire BeforeToolExecution events on the carrier thread, in request order, before fan-out.
        // Keeps observability semantics predictable: subscribers always see "before" in request order.
        if (beforeToolExecutionHandler != null) {
            for (ToolExecutionRequest request : requests) {
                beforeToolExecutionHandler.accept(BeforeToolExecution.builder()
                        .request(request)
                        .invocationContext(invocationContext)
                        .build());
            }
        }

        addToMemory(aiMessage);

        Semaphore semaphore = dispatcher.semaphore();
        ToolExecutionResultMessage[] results = new ToolExecutionResultMessage[n];
        Future<?>[] futures = new Future<?>[n];

        for (int i = 0; i < n; i++) {
            final int index = i;
            final ToolExecutionRequest request = requests.get(i);
            futures[i] = VirtualThreadsRecorder.getCurrent().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        runParallelToolTask(request, index, results, semaphore);
                    } catch (Throwable t) {
                        // Make sure the slot is filled so memory stays consistent (one result per
                        // request) regardless of how the failure surfaced — uncaught exceptions
                        // from tools without a configured error handler, PreventsErrorHandlerExecution
                        // throws, or Errors that escape executeTool's catch.
                        if (results[index] == null) {
                            results[index] = ToolExecutionResultMessage.from(request, errorResultText(t));
                        }
                        if (t instanceof RuntimeException re) {
                            throw re;
                        }
                        if (t instanceof Error err) {
                            throw err;
                        }
                        throw new RuntimeException(t);
                    }
                }
            });
        }

        // Wait for every task. Capture the first error so we can rethrow after each slot has
        // either a real result, a cancellation marker, or a synthesized error result.
        Throwable firstError = null;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                if (firstError == null) {
                    firstError = ie;
                }
            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                if (firstError == null) {
                    firstError = cause;
                }
            }
        }

        // Memory now holds one result per request — success, cancellation marker, or synthesized
        // error result — so the conversation stays consistent even if onError fires below.
        for (ToolExecutionResultMessage message : results) {
            addToMemory(message);
        }

        if (firstError != null) {
            if (firstError instanceof RuntimeException re) {
                throw re;
            }
            if (firstError instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(firstError);
        }
    }

    private static String errorResultText(Throwable t) {
        String message = t.getMessage();
        return "Tool execution failed: " + (message != null ? message : t.getClass().getSimpleName());
    }

    private void runParallelToolTask(ToolExecutionRequest request, int index,
            ToolExecutionResultMessage[] results, Semaphore semaphore) {
        if (isCancelled()) {
            results[index] = ToolExecutionResultMessage.from(request, "Tool execution was cancelled");
            return;
        }
        boolean acquired = false;
        try {
            if (semaphore != null) {
                semaphore.acquire();
                acquired = true;
            }
            if (isCancelled()) {
                results[index] = ToolExecutionResultMessage.from(request, "Tool execution was cancelled");
                return;
            }
            ToolExecutor toolExecutor = toolExecutors.get(request.name());
            ToolExecutionResult toolExecutionResult = executeTool(
                    request,
                    toolExecutor,
                    invocationContext,
                    context.toolService.argumentsErrorHandler(),
                    context.toolService.executionErrorHandler());

            fireToolExecutedEvent(request, toolExecutionResult.resultText());

            if (toolExecuteHandler != null) {
                toolExecuteHandler.accept(ToolExecution.builder()
                        .request(request)
                        .result(toolExecutionResult)
                        .invocationContext(invocationContext)
                        .build());
            }

            results[index] = ToolExecutionResultMessage.from(request, toolExecutionResult.resultText());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private void runSerialToolBatch(AiMessage aiMessage) {
        addToMemory(aiMessage);
        List<ToolExecutionRequest> toolExecutionRequests = aiMessage.toolExecutionRequests();
        int maxToolCallsPerResponse;
        if (context.maxToolCallsPerResponse != null && context.maxToolCallsPerResponse != 0) {
            maxToolCallsPerResponse = context.maxToolCallsPerResponse;
        } else {
            maxToolCallsPerResponse = ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.langchain4j.ai-service.max-tool-calls-per-response", Integer.class)
                    .orElse(0);
        }
        int toolCallsCount = 0;
        for (ToolExecutionRequest toolExecutionRequest : toolExecutionRequests) {
            if (maxToolCallsPerResponse > 0 && toolCallsCount >= maxToolCallsPerResponse) {
                throw new ToolCallsLimitExceededException(maxToolCallsPerResponse,
                        toolExecutionRequests.size());
            }
            toolCallsCount++;
            if (isCancelled()) {
                // Fill cancelled tools with error results to keep memory consistent:
                // every tool request must have a matching tool result
                ToolExecutionResultMessage cancelledResult = ToolExecutionResultMessage.from(
                        toolExecutionRequest, "Tool execution was cancelled");
                addToMemory(cancelledResult);
                continue;
            }
            if (beforeToolExecutionHandler != null) {
                BeforeToolExecution beforeToolExecution = BeforeToolExecution.builder()
                        .request(toolExecutionRequest)
                        .invocationContext(invocationContext)
                        .build();
                beforeToolExecutionHandler.accept(beforeToolExecution);
            }

            String toolName = toolExecutionRequest.name();
            ToolExecutor toolExecutor = toolExecutors.get(toolName);
            // Execute the tool with argumentsErrorHandler and executionErrorHandler.
            // If execution fails, these handlers convert exceptions into error results
            // that are sent back to the LLM, allowing it to recover from tool failures.
            ToolExecutionResult toolExecutionResult = executeTool(
                    toolExecutionRequest,
                    toolExecutor,
                    invocationContext,
                    context.toolService.argumentsErrorHandler(),
                    context.toolService.executionErrorHandler());

            fireToolExecutedEvent(toolExecutionRequest, toolExecutionResult.resultText());

            ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                    toolExecutionRequest,
                    toolExecutionResult.resultText());

            ToolExecution toolExecution = ToolExecution.builder()
                    .request(toolExecutionRequest)
                    .result(toolExecutionResult)
                    .invocationContext(invocationContext)
                    .build();
            if (toolExecuteHandler != null) {
                toolExecuteHandler.accept(toolExecution);
            }
            addToMemory(toolExecutionResultMessage);
        }
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        if (isCancelled()) {
            shutdown();
            return;
        }
        fireResponseReceivedEvent(completeResponse);
        AiMessage aiMessage = completeResponse.aiMessage();

        if (aiMessage.hasToolExecutionRequests()) {
            // Fire IntermediateResponseEvent immediately before tool execution
            // This ensures the event reaches clients as soon as streaming completes,
            // without waiting for executor scheduling latency
            if (intermediateResponseHandler != null) {
                intermediateResponseHandler.accept(completeResponse);
            }
            ToolsDispatcher dispatcher = toolsDispatcher();
            BatchDispatchDecision decision = determineBatchDispatchDecision(aiMessage.toolExecutionRequests());
            boolean dispatchToVirtualThread = shouldDispatchToVirtualThread(decision, dispatcher);
            // A single-tool batch has nothing to parallelize: fan-out would just spawn an extra
            // virtual thread and block the carrier on Future#get for no concurrency benefit.
            boolean parallelBatch = dispatchToVirtualThread
                    && dispatcher.parallelVirtualThreadBatch()
                    && aiMessage.toolExecutionRequests().size() > 1;
            // Tools execution may block the caller thread. When the caller thread is the event loop thread, and
            // when tools have been detected to be potentially blocking, we need to switch to a worker thread.
            executeTools(new Runnable() {
                @Override
                public void run() {
                    if (isCancelled()) {
                        shutdown();
                        return;
                    }
                    if (parallelBatch) {
                        runParallelToolBatch(aiMessage, dispatcher);
                    } else {
                        runSerialToolBatch(aiMessage);
                    }

                    if (isCancelled()) {
                        shutdown();
                        return;
                    }

                    DefaultChatRequestParameters.Builder<?> parametersBuilder = ChatRequestParameters.builder();
                    parametersBuilder.toolSpecifications(toolSpecifications);

                    StreamingChatModel effectiveStreamingChatModel = context.effectiveStreamingChatModel(methodCreateInfo,
                            methodArgs);
                    if (nonNull(effectiveStreamingChatModel.defaultRequestParameters())) {
                        var toolChoice = effectiveStreamingChatModel.defaultRequestParameters().toolChoice();
                        if (nonNull(toolChoice) && toolChoice.equals(ToolChoice.REQUIRED)) {
                            // This code is needed to avoid a infinite-loop when using the AiService
                            // in combination with the tool-choice option set to REQUIRED.
                            // If the tool-choice option is not set to AUTO after calling the tool,
                            // the model may continuously reselect the same tool in subsequent responses,
                            // even though the tool has already been invoked.
                            parametersBuilder.toolChoice(ToolChoice.AUTO);
                        }
                    }

                    ChatRequest chatRequest = ChatRequest.builder()
                            .messages(messagesToSend(memoryId))
                            .parameters(parametersBuilder.build())
                            .build();
                    QuarkusAiServiceStreamingResponseHandler handler = new QuarkusAiServiceStreamingResponseHandler(
                            chatRequest,
                            context,
                            invocationContext,
                            memoryId,
                            partialResponseHandler,
                            partialThinkingHandler,
                            partialToolCallHandler,
                            beforeToolExecutionHandler,
                            intermediateResponseHandler,
                            toolExecuteHandler,
                            completeResponseHandler,
                            completionHandler,
                            errorHandler,
                            temporaryMemory,
                            TokenUsage.sum(tokenUsage, completeResponse.metadata().tokenUsage()),
                            toolSpecifications,
                            toolExecutors,
                            mustSwitchToWorkerThread, switchToWorkerForEmission, executionContext, executor, methodCreateInfo,
                            methodArgs,
                            cancelled);

                    fireRequestIssuedEvent(chatRequest);
                    effectiveStreamingChatModel.chat(chatRequest, handler);
                }
            }, dispatchToVirtualThread, parallelBatch, dispatcher);
        } else {
            if (completeResponseHandler != null) {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ChatResponse finalChatResponse = ChatResponse.builder()
                                    .aiMessage(aiMessage)
                                    .metadata(ChatResponseMetadata.builder()
                                            .id(completeResponse.metadata().id())
                                            .modelName(completeResponse.metadata().modelName())
                                            .tokenUsage(TokenUsage.sum(tokenUsage, completeResponse.metadata().tokenUsage()))
                                            .finishReason(completeResponse.metadata().finishReason())
                                            .build())
                                    .build();
                            fireInvocationComplete(finalChatResponse);
                            addToMemory(aiMessage);
                            completeResponseHandler.accept(finalChatResponse);
                        } finally {
                            shutdown(); // Terminal event, we can shutdown the executor
                        }
                    }
                };
                execute(runnable);
            } else if (completionHandler != null) {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        Response<AiMessage> finalResponse = Response.from(aiMessage,
                                TokenUsage.sum(tokenUsage, completeResponse.metadata().tokenUsage()),
                                completeResponse.metadata().finishReason());
                        fireInvocationComplete(finalResponse);
                        addToMemory(aiMessage);
                        completionHandler.accept(finalResponse);
                    }
                };
                execute(runnable);
            }
        }
    }

    private void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    private boolean isCancelled() {
        return cancelled.get();
    }

    private void addToMemory(ChatMessage chatMessage) {
        if (context.hasChatMemory()) {
            context.chatMemoryService.getChatMemory(memoryId).add(chatMessage);
        } else {
            temporaryMemory.add(chatMessage);
        }
    }

    private List<ChatMessage> messagesToSend(Object memoryId) {
        return context.hasChatMemory()
                ? context.chatMemoryService.getChatMemory(memoryId).messages()
                : temporaryMemory;
    }

    @Override
    public void onError(Throwable error) {
        fireErrorReceived(error);

        if (errorHandler != null) {
            execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        errorHandler.accept(error);
                    } catch (Exception e) {
                        log.error("While handling the following error...", error);
                        log.error("...the following error happened", e);
                    } finally {
                        shutdown(); // Terminal event, we can shutdown the executor
                    }
                }
            });
        } else {
            log.warn("Ignored error", error);
        }
    }
}
