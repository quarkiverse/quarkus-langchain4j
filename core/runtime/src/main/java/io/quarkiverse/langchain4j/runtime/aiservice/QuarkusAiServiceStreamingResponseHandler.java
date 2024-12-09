package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import io.vertx.core.Context;

/**
 * A {@link StreamingResponseHandler} implementation for Quarkus.
 * The main difference with the upstream implementation is the thread switch when receiving the `completion` event
 * when there is tool execution requests.
 */
public class QuarkusAiServiceStreamingResponseHandler implements StreamingResponseHandler<AiMessage> {

    private final Logger log = Logger.getLogger(QuarkusAiServiceStreamingResponseHandler.class);

    private final AiServiceContext context;
    private final Object memoryId;

    private final Consumer<String> tokenHandler;
    private final Consumer<Response<AiMessage>> completionHandler;
    private final Consumer<ToolExecution> toolExecuteHandler;
    private final Consumer<Throwable> errorHandler;

    private final List<ChatMessage> temporaryMemory;
    private final TokenUsage tokenUsage;

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final Context executionContext;
    private final boolean mustSwitchToWorkerThread;
    private final boolean switchToWorkerForEmission;
    private final ExecutorService executor;

    QuarkusAiServiceStreamingResponseHandler(AiServiceContext context,
            Object memoryId,
            Consumer<String> tokenHandler,
            Consumer<ToolExecution> toolExecuteHandler,
            Consumer<Response<AiMessage>> completionHandler,
            Consumer<Throwable> errorHandler,
            List<ChatMessage> temporaryMemory,
            TokenUsage tokenUsage,
            List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors,
            boolean mustSwitchToWorkerThread,
            boolean switchToWorkerForEmission,
            Context cxtx) {
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");

        this.tokenHandler = ensureNotNull(tokenHandler, "tokenHandler");
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
        if (executionContext == null) {
            // We do not have a context, but we still need to make sure we are not blocking the event loop and ordered
            // is respected.
            executor = Executors.newSingleThreadExecutor();
        } else {
            executor = null;
        }
    }

    public QuarkusAiServiceStreamingResponseHandler(AiServiceContext context, Object memoryId, Consumer<String> tokenHandler,
            Consumer<ToolExecution> toolExecuteHandler, Consumer<Response<AiMessage>> completionHandler,
            Consumer<Throwable> errorHandler, List<ChatMessage> temporaryMemory, TokenUsage sum,
            List<ToolSpecification> toolSpecifications, Map<String, ToolExecutor> toolExecutors,
            boolean mustSwitchToWorkerThread, boolean switchToWorkerForEmission, Context executionContext,
            ExecutorService executor) {
        this.context = context;
        this.memoryId = memoryId;
        this.tokenHandler = tokenHandler;
        this.toolExecuteHandler = toolExecuteHandler;
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
    }

    @Override
    public void onNext(String token) {
        execute(new Runnable() {
            @Override
            public void run() {
                tokenHandler.accept(token);
            }
        });

    }

    private void executeTools(Runnable runnable) {
        if (mustSwitchToWorkerThread && Context.isOnEventLoopThread()) {
            executeOnWorkerThread(runnable);
        } else {
            runnable.run();
        }
    }

    private void execute(Runnable runnable) {
        if (switchToWorkerForEmission && Context.isOnEventLoopThread()) {
            executeOnWorkerThread(runnable);
        } else {
            runnable.run();
        }
    }

    private void executeOnWorkerThread(Runnable runnable) {
        if (executionContext != null) {
            executionContext.executeBlocking(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    runnable.run();
                    return null;
                }
            }, true);
        } else {
            executor.submit(runnable);
        }
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
        AiMessage aiMessage = response.content();

        if (aiMessage.hasToolExecutionRequests()) {
            // Tools execution may block the caller thread. When the caller thread is the event loop thread, and
            // when tools have been detected to be potentially blocking, we need to switch to a worker thread.
            executeTools(new Runnable() {
                @Override
                public void run() {
                    addToMemory(aiMessage);
                    for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                        String toolName = toolExecutionRequest.name();
                        ToolExecutor toolExecutor = toolExecutors.get(toolName);
                        String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                        ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                                toolExecutionRequest,
                                toolExecutionResult);
                        ToolExecution toolExecution = ToolExecution.builder()
                                .request(toolExecutionRequest).result(toolExecutionResult)
                                .build();
                        if (toolExecuteHandler != null) {
                            toolExecuteHandler.accept(toolExecution);
                        }
                        QuarkusAiServiceStreamingResponseHandler.this.addToMemory(toolExecutionResultMessage);
                    }

                    context.streamingChatModel.generate(
                            QuarkusAiServiceStreamingResponseHandler.this.messagesToSend(memoryId),
                            toolSpecifications,
                            new QuarkusAiServiceStreamingResponseHandler(
                                    context,
                                    memoryId,
                                    tokenHandler,
                                    toolExecuteHandler,
                                    completionHandler,
                                    errorHandler,
                                    temporaryMemory,
                                    TokenUsage.sum(tokenUsage, response.tokenUsage()),
                                    toolSpecifications,
                                    toolExecutors,
                                    mustSwitchToWorkerThread, switchToWorkerForEmission, executionContext, executor));
                }
            });
        } else {
            if (completionHandler != null) {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            addToMemory(aiMessage);
                            completionHandler.accept(Response.from(
                                    aiMessage,
                                    TokenUsage.sum(tokenUsage, response.tokenUsage()),
                                    response.finishReason()));
                        } finally {
                            shutdown(); // Terminal event, we can shutdown the executor
                        }
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

    private void addToMemory(ChatMessage chatMessage) {
        if (context.hasChatMemory()) {
            context.chatMemory(memoryId).add(chatMessage);
        } else {
            temporaryMemory.add(chatMessage);
        }
    }

    private List<ChatMessage> messagesToSend(Object memoryId) {
        return context.hasChatMemory()
                ? context.chatMemory(memoryId).messages()
                : temporaryMemory;
    }

    @Override
    public void onError(Throwable error) {
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
