package io.quarkiverse.langchain4j.runtime.devui;

import static io.quarkiverse.langchain4j.runtime.LangChain4jUtil.chatMessageToText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Any;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.devui.json.ChatMessagePojo;
import io.quarkiverse.langchain4j.runtime.devui.json.ChatResultPojo;
import io.quarkiverse.langchain4j.runtime.devui.json.ToolExecutionRequestPojo;
import io.quarkiverse.langchain4j.runtime.devui.json.ToolExecutionResultPojo;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutorFactory;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.arc.All;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.vertx.core.json.JsonObject;

@ActivateRequestContext
public class ChatJsonRPCService {

    public static final int MAX_SEQUENTIAL_TOOL_EXECUTIONS = 20;
    private final ChatModel model;
    private final Optional<StreamingChatModel> streamingModel;

    private final ChatMemoryProvider memoryProvider;

    // The augmentor to use, if any is found in the application. Only augmentors that are CDI beans
    // can be found. If more than one is found, for now we choose the first one offered by the CDI container.
    // FIXME: perhaps the UI could offer choosing between available augmentors when there are more
    private RetrievalAugmentor retrievalAugmentor;

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final ToolProvider toolProvider;

    public ChatJsonRPCService(@All List<ChatModel> models, // don't use ChatModel model because it results in the default model not being configured
            @All List<StreamingChatModel> streamingModels,
            @All List<Supplier<RetrievalAugmentor>> retrievalAugmentorSuppliers,
            @All List<RetrievalAugmentor> retrievalAugmentors,
            ChatMemoryProvider memoryProvider,
            QuarkusToolExecutorFactory toolExecutorFactory,
            @All List<Supplier<ToolProvider>> toolProvidersSuppliers,
            @All List<ToolProvider> toolProviders) {
        this.model = models.get(0);
        this.toolProvider = getToolProvider(toolProvidersSuppliers, toolProviders);
        this.streamingModel = streamingModels.isEmpty() ? Optional.empty() : Optional.of(streamingModels.get(0));
        this.retrievalAugmentor = null;
        for (Supplier<RetrievalAugmentor> supplier : retrievalAugmentorSuppliers) {
            this.retrievalAugmentor = supplier.get();
            if (this.retrievalAugmentor != null) {
                break;
            }
        }
        if (this.retrievalAugmentor == null) {
            for (RetrievalAugmentor augmentorFromCdi : retrievalAugmentors) {
                this.retrievalAugmentor = augmentorFromCdi;
                if (this.retrievalAugmentor != null) {
                    break;
                }
            }
        }
        this.memoryProvider = memoryProvider;
        // retrieve available tools
        Map<String, List<ToolMethodCreateInfo>> toolsMetadata = ToolsRecorder.getMetadata();
        if (toolsMetadata != null && this.toolProvider == null) {
            toolExecutors = new HashMap<>();
            toolSpecifications = new ArrayList<>();
            for (Map.Entry<String, List<ToolMethodCreateInfo>> entry : toolsMetadata.entrySet()) {
                for (ToolMethodCreateInfo methodCreateInfo : entry.getValue()) {
                    Object objectWithTool = null;
                    try {
                        objectWithTool = Arc.container().select(
                                Thread.currentThread().getContextClassLoader().loadClass(entry.getKey()),
                                new Any.Literal()).get();
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    QuarkusToolExecutor.Context executorContext = new QuarkusToolExecutor.Context(objectWithTool,
                            methodCreateInfo.invokerClassName(), methodCreateInfo.methodName(),
                            methodCreateInfo.argumentMapperClassName(), methodCreateInfo.executionModel());
                    toolExecutors.put(methodCreateInfo.toolSpecification().name(),
                            toolExecutorFactory.create(executorContext));
                    toolSpecifications.add(methodCreateInfo.toolSpecification());
                }
            }

        } else if (this.toolProvider != null) {
            // mutable list / map
            toolExecutors = new HashMap<>();
            toolSpecifications = new ArrayList<>();
        } else {
            toolSpecifications = List.of();
            toolExecutors = Map.of();
        }
    }

    private final AtomicReference<ChatMemory> currentMemory = new AtomicReference<>();
    private final AtomicLong currentMemoryId = new AtomicLong();

    public String reset(String systemMessage) {
        if (currentMemory.get() != null) {
            currentMemory.get().clear();
        }
        long memoryId = ThreadLocalRandom.current().nextLong();
        currentMemoryId.set(memoryId);
        ChatMemory memory = memoryProvider.get(memoryId);
        currentMemory.set(memory);
        if (systemMessage != null && !systemMessage.isEmpty()) {
            memory.add(new SystemMessage(systemMessage));
        }
        return "OK";
    }

    public boolean isStreamingChatSupported() {
        return streamingModel.isPresent();
    }

    public Multi<JsonObject> streamingChat(String message, boolean ragEnabled) {
        ChatMemory m = currentMemory.get();
        if (m == null) {
            reset("");
            m = currentMemory.get();
        }
        final ChatMemory memory = m;

        // create a backup of the chat memory, because we are now going to
        // add a new message to it, and might have to remove it if the chat
        // request fails - unfortunately the ChatMemory API doesn't allow
        // removing single messages
        List<ChatMessage> chatMemoryBackup = memory.messages();

        Multi<JsonObject> stream = Multi.createFrom().emitter(em -> {
            try {
                // invoke RAG is applicable
                UserMessage userMessage = UserMessage.from(message);
                if (retrievalAugmentor != null && ragEnabled) {
                    Metadata metadata = Metadata.from(userMessage, currentMemoryId.get(), memory.messages());
                    AugmentationRequest augmentationRequest = new AugmentationRequest(userMessage, metadata);
                    ChatMessage augmentedMessage = retrievalAugmentor.augment(augmentationRequest).chatMessage();
                    memory.add(augmentedMessage);
                    em.emit(new JsonObject().put("augmentedMessage", chatMessageToText(augmentedMessage)));
                } else {
                    memory.add(new UserMessage(message));
                }

                StreamingChatModel streamingModel = this.streamingModel.orElseThrow(IllegalStateException::new);
                setToolsViaProviderIfAvailable(memory, userMessage);

                // invoke tools if applicable
                if (toolSpecifications.isEmpty()) {
                    streamingModel.chat(memory.messages(), new StreamingChatResponseHandler() {
                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            memory.add(response.aiMessage());
                            String message = response.aiMessage().text();
                            em.emit(new JsonObject().put("message", message));
                            em.complete();
                        }

                        @Override
                        public void onPartialResponse(String token) {
                            em.emit(new JsonObject().put("token", token));
                        }

                        @Override
                        public void onError(Throwable error) {
                            em.fail(error);
                        }
                    });
                } else {
                    executeWithToolsAndStreaming(memory, em, MAX_SEQUENTIAL_TOOL_EXECUTIONS);
                }
            } catch (Throwable t) {
                // restore the memory from the backup
                memory.clear();
                chatMemoryBackup.forEach(memory::add);
                Log.warn(t);
                em.fail(t);
            }
        });
        // run on a worker thread because the retrieval augmentor might be blocking
        return stream.runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    public ChatResultPojo chat(String message, boolean ragEnabled) {
        ChatMemory memory = currentMemory.get();
        if (memory == null) {
            reset("");
            memory = currentMemory.get();
        }
        // create a backup of the chat memory, because we are now going to
        // add a new message to it, and might have to remove it if the chat
        // request fails - unfortunately the ChatMemory API doesn't allow
        // removing single messages
        List<ChatMessage> chatMemoryBackup = memory.messages();
        try {
            UserMessage userMessage = UserMessage.from(message);
            if (retrievalAugmentor != null && ragEnabled) {
                Metadata metadata = Metadata.from(userMessage, currentMemoryId.get(), memory.messages());
                AugmentationRequest augmentationRequest = new AugmentationRequest(userMessage, metadata);
                ChatMessage augmentedMessage = retrievalAugmentor.augment(augmentationRequest).chatMessage();
                memory.add(augmentedMessage);
            } else {
                memory.add(new UserMessage(message));
            }

            boolean hasToolProvider = setToolsViaProviderIfAvailable(memory, userMessage);

            if (toolSpecifications.isEmpty()) {
                ChatResponse modelResponse = model.chat(memory.messages());
                memory.add(modelResponse.aiMessage());
            } else {
                executeWithTools(memory);
            }
            // Remove toolProviderSupplier tools again
            if (hasToolProvider) {
                toolSpecifications.clear();
                toolExecutors.clear();
            }
            List<ChatMessagePojo> response = ChatMessagePojo.listFromMemory(memory);
            return new ChatResultPojo(response, null);
        } catch (Throwable t) {
            // restore the memory from the backup
            memory.clear();
            chatMemoryBackup.forEach(memory::add);
            Log.warn(t);
            return new ChatResultPojo(null, t.getMessage());
        }
    }

    // FIXME: this was basically copied from `dev.langchain4j.service.DefaultAiServices`,
    // maybe it could be extracted into a reusable piece of code
    private Response<AiMessage> executeWithTools(ChatMemory memory) {
        ChatResponse response = model
                .chat(ChatRequest.builder().messages(memory.messages()).toolSpecifications(toolSpecifications).build());
        int MAX_SEQUENTIAL_TOOL_EXECUTIONS = 20;
        int executionsLeft = MAX_SEQUENTIAL_TOOL_EXECUTIONS;
        while (true) {
            if (executionsLeft-- == 0) {
                throw new RuntimeException(
                        "Something is wrong, exceeded " + MAX_SEQUENTIAL_TOOL_EXECUTIONS + " sequential tool executions");
            }
            AiMessage aiMessage = response.aiMessage();
            memory.add(aiMessage);
            if (!aiMessage.hasToolExecutionRequests()) {
                break;
            }
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                ToolExecutor toolExecutor = toolExecutors.get(toolExecutionRequest.name());
                String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, currentMemoryId.get());
                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                        toolExecutionRequest,
                        toolExecutionResult);
                memory.add(toolExecutionResultMessage);
            }
            response = model
                    .chat(ChatRequest.builder().messages(memory.messages()).toolSpecifications(toolSpecifications).build());
        }
        return Response.from(response.aiMessage(), new TokenUsage(), response.finishReason());
    }

    private void executeWithToolsAndStreaming(ChatMemory memory,
            MultiEmitter<? super JsonObject> em,
            int toolExecutionsLeft) {
        toolExecutionsLeft--;
        if (toolExecutionsLeft == 0) {
            throw new RuntimeException(
                    "Something is wrong, exceeded " + MAX_SEQUENTIAL_TOOL_EXECUTIONS + " sequential tool executions");
        }
        int finalToolExecutionsLeft = toolExecutionsLeft;
        ChatRequest chatRequest = ChatRequest.builder().messages(memory.messages()).toolSpecifications(toolSpecifications)
                .build();
        streamingModel.get().chat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse response) {
                // run on a worker thread because the tool might be blocking
                Infrastructure.getDefaultExecutor().execute(() -> {
                    AiMessage aiMessage = response.aiMessage();
                    memory.add(aiMessage);
                    if (!aiMessage.hasToolExecutionRequests()) {
                        em.emit(new JsonObject().put("message", aiMessage.text()));
                        em.complete();
                    } else {
                        for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                            ToolExecutor toolExecutor = toolExecutors.get(toolExecutionRequest.name());
                            ToolExecutionRequestPojo toolExecutionRequestPojo = new ToolExecutionRequestPojo(
                                    toolExecutionRequest.id(), toolExecutionRequest.name(), toolExecutionRequest.arguments());
                            em.emit(new JsonObject().put("toolExecutionRequest", toolExecutionRequestPojo));
                            String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, currentMemoryId.get());
                            ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage
                                    .from(toolExecutionRequest, toolExecutionResult);
                            memory.add(toolExecutionResultMessage);
                            ToolExecutionResultPojo toolExecutionResultPojo = new ToolExecutionResultPojo(
                                    toolExecutionResultMessage.id(), toolExecutionResultMessage.toolName(),
                                    toolExecutionResultMessage.text());
                            em.emit(new JsonObject().put("toolExecutionResult", toolExecutionResultPojo));
                        }
                        executeWithToolsAndStreaming(memory, em, finalToolExecutionsLeft);
                    }
                    // Remove toolProviderSupplier tools again
                    if (toolProvider != null) {
                        toolSpecifications.clear();
                        toolExecutors.clear();
                    }
                });
            }

            @Override
            public void onPartialResponse(String token) {
                em.emit(new JsonObject().put("token", token));
            }

            @Override
            public void onError(Throwable error) {
                em.fail(error);
            }
        });
    }

    /**
     * Get the first available tool provider that we can find in the CDI container...
     */
    private ToolProvider getToolProvider(List<Supplier<ToolProvider>> toolProviderSuppliers,
            List<ToolProvider> toolProviders) {
        for (Supplier<ToolProvider> provider : toolProviderSuppliers) {
            if (provider.get() != null) {
                return provider.get();
            }
        }
        for (ToolProvider provider : toolProviders) {
            if (provider != null) {
                return provider;
            }
        }
        return null;
    }

    private boolean setToolsViaProviderIfAvailable(ChatMemory memory, UserMessage userMessage) {
        boolean hasToolProvider = toolProvider != null;
        if (hasToolProvider) {
            ToolProviderRequest toolRequest = new ToolProviderRequest(memory, userMessage);
            ToolProviderResult toolsResult = toolProvider.provideTools(toolRequest);
            for (ToolSpecification specification : toolsResult.tools().keySet()) {
                toolSpecifications.add(specification);
                toolExecutors.put(specification.name(), toolsResult.tools().get(specification));
            }
        }
        return hasToolProvider;
    }
}
