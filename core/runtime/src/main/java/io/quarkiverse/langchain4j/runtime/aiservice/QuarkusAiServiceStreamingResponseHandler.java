package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Objects.nonNull;

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
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import io.vertx.core.Context;

/**
 * A {@link StreamingResponseHandler} implementation for Quarkus. The main difference with the upstream implementation is the
 * thread switch when
 * receiving the `completion` event when there is tool execution requests.
 */
public class QuarkusAiServiceStreamingResponseHandler implements StreamingChatResponseHandler {

    private final Logger log = Logger.getLogger(QuarkusAiServiceStreamingResponseHandler.class);

    private final AiServiceContext context;
    private final Object memoryId;

    private final Consumer<String> partialResponseHandler;
    private final Consumer<Response<AiMessage>> completionHandler;
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
    private final ExecutorService executor;

    QuarkusAiServiceStreamingResponseHandler(AiServiceContext context,
            Object memoryId,
            Consumer<String> partialResponseHandler,
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
            Context cxtx) {
        this.context = ensureNotNull(context, "context");
        this.memoryId = ensureNotNull(memoryId, "memoryId");

        this.partialResponseHandler = ensureNotNull(partialResponseHandler, "partialResponseHandler");
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
        if (executionContext == null) {
            // We do not have a context, but we still need to make sure we are not blocking the event loop and ordered
            // is respected.
            executor = Executors.newSingleThreadExecutor();
        } else {
            executor = null;
        }
    }

    public QuarkusAiServiceStreamingResponseHandler(AiServiceContext context, Object memoryId,
            Consumer<String> partialResponseHandler,
            Consumer<ToolExecution> toolExecuteHandler, Consumer<ChatResponse> completeResponseHandler,
            Consumer<Response<AiMessage>> completionHandler,
            Consumer<Throwable> errorHandler, List<ChatMessage> temporaryMemory, TokenUsage sum,
            List<ToolSpecification> toolSpecifications, Map<String, ToolExecutor> toolExecutors,
            boolean mustSwitchToWorkerThread, boolean switchToWorkerForEmission, Context executionContext,
            ExecutorService executor) {
        this.context = context;
        this.memoryId = memoryId;
        this.partialResponseHandler = ensureNotNull(partialResponseHandler, "partialResponseHandler");
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
    }

    @Override
    public void onPartialResponse(String partialResponse) {
        execute(new Runnable() {
            @Override
            public void run() {
                partialResponseHandler.accept(partialResponse);
            }
        });

    }

    private void executeTools(Runnable runnable) {
        if (mustSwitchToWorkerThread && Context.isOnEventLoopThread()) {
            executeOnWorkerThread(runnable, false);
        } else {
            runnable.run();
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

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        AiMessage aiMessage = completeResponse.aiMessage();

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

                    DefaultChatRequestParameters.Builder<?> parametersBuilder = ChatRequestParameters.builder();
                    parametersBuilder.toolSpecifications(toolSpecifications);

                    if (nonNull(context.streamingChatModel.defaultRequestParameters())) {
                        var toolChoice = context.streamingChatModel.defaultRequestParameters().toolChoice();
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
                            context,
                            memoryId,
                            partialResponseHandler,
                            toolExecuteHandler,
                            completeResponseHandler,
                            completionHandler,
                            errorHandler,
                            temporaryMemory,
                            TokenUsage.sum(tokenUsage, completeResponse.metadata().tokenUsage()),
                            toolSpecifications,
                            toolExecutors,
                            mustSwitchToWorkerThread, switchToWorkerForEmission, executionContext, executor);
                    context.streamingChatModel.chat(chatRequest, handler);
                }
            });
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
