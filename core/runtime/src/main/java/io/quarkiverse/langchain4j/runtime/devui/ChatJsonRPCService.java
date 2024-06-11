package io.quarkiverse.langchain4j.runtime.devui;

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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.Metadata;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.devui.json.ChatMessagePojo;
import io.quarkiverse.langchain4j.runtime.devui.json.ChatResultPojo;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutorFactory;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkus.arc.All;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.vertx.core.json.JsonObject;

@ActivateRequestContext
public class ChatJsonRPCService {

    private final ChatLanguageModel model;
    private final Optional<StreamingChatLanguageModel> streamingModel;

    private final ChatMemoryProvider memoryProvider;

    // The augmentor to use, if any is found in the application. Only augmentors that are CDI beans
    // can be found. If more than one is found, for now we choose the first one offered by the CDI container.
    // FIXME: perhaps the UI could offer choosing between available augmentors when there are more
    private RetrievalAugmentor retrievalAugmentor;

    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;

    public ChatJsonRPCService(@All List<ChatLanguageModel> models, // don't use ChatLanguageModel model because it results in the default model not being configured
            @All List<StreamingChatLanguageModel> streamingModels,
            @All List<Supplier<RetrievalAugmentor>> retrievalAugmentorSuppliers,
            @All List<RetrievalAugmentor> retrievalAugmentors,
            ChatMemoryProvider memoryProvider,
            QuarkusToolExecutorFactory toolExecutorFactory) {
        this.model = models.get(0);
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
        if (toolsMetadata != null) {
            toolExecutors = new HashMap<>();
            toolSpecifications = new ArrayList<>();
            for (Map.Entry<String, List<ToolMethodCreateInfo>> entry : toolsMetadata.entrySet()) {
                for (ToolMethodCreateInfo methodCreateInfo : entry.getValue()) {
                    Object objectWithTool = null;
                    try {
                        objectWithTool = Arc.container().select(
                                Thread.currentThread().getContextClassLoader().loadClass(entry.getKey())).get();
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    QuarkusToolExecutor.Context executorContext = new QuarkusToolExecutor.Context(objectWithTool,
                            methodCreateInfo.invokerClassName(), methodCreateInfo.methodName(),
                            methodCreateInfo.argumentMapperClassName());
                    toolExecutors.put(methodCreateInfo.toolSpecification().name(),
                            toolExecutorFactory.create(executorContext));
                    toolSpecifications.add(methodCreateInfo.toolSpecification());
                }
            }
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

        return Multi.createFrom().emitter(em -> {
            try {
                if (retrievalAugmentor != null && ragEnabled) {
                    UserMessage userMessage = UserMessage.from(message);
                    Metadata metadata = Metadata.from(userMessage, currentMemoryId.get(), memory.messages());
                    memory.add(retrievalAugmentor.augment(userMessage, metadata));
                } else {

                    memory.add(new UserMessage(message));
                }

                StreamingChatLanguageModel streamingModel = this.streamingModel.orElseThrow(IllegalStateException::new);

                streamingModel.generate(memory.messages(), new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        memory.add(response.content());
                        String message = response.content().text();
                        em.emit(new JsonObject().put("message", message));
                        em.complete();
                    }

                    @Override
                    public void onNext(String token) {
                        em.emit(new JsonObject().put("token", token));
                    }

                    @Override
                    public void onError(Throwable error) {
                        em.fail(error);
                    }
                });
            } catch (Throwable t) {
                // restore the memory from the backup
                memory.clear();
                chatMemoryBackup.forEach(memory::add);
                Log.warn(t);
                em.fail(t);
            }
        });
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
            if (retrievalAugmentor != null && ragEnabled) {
                UserMessage userMessage = UserMessage.from(message);
                Metadata metadata = Metadata.from(userMessage, currentMemoryId.get(), memory.messages());
                memory.add(retrievalAugmentor.augment(userMessage, metadata));
            } else {
                memory.add(new UserMessage(message));
            }

            Response<AiMessage> modelResponse;
            if (toolSpecifications.isEmpty()) {
                modelResponse = model.generate(memory.messages());
                memory.add(modelResponse.content());
            } else {
                modelResponse = executeWithTools(memory);
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
    public Response<AiMessage> executeWithTools(ChatMemory memory) {
        Response<AiMessage> response = model.generate(memory.messages(), toolSpecifications);
        int MAX_SEQUENTIAL_TOOL_EXECUTIONS = 20;
        int executionsLeft = MAX_SEQUENTIAL_TOOL_EXECUTIONS;
        while (true) {
            if (executionsLeft-- == 0) {
                throw new RuntimeException(
                        "Something is wrong, exceeded " + MAX_SEQUENTIAL_TOOL_EXECUTIONS + " sequential tool executions");
            }
            AiMessage aiMessage = response.content();
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
            response = model.generate(memory.messages(), toolSpecifications);
        }
        return Response.from(response.content(), new TokenUsage(), response.finishReason());
    }

}
