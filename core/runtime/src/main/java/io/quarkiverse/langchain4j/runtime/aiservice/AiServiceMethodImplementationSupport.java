package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.output.TokenUsage.sum;
import static dev.langchain4j.service.AiServices.removeToolMessages;
import static dev.langchain4j.service.AiServices.verifyModerationIfNeeded;
import static io.quarkiverse.langchain4j.runtime.ResponseSchemaUtil.hasResponseSchema;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServiceTokenStream;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.spi.ServiceHelper;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.InitialMessagesCreatedEvent;
import io.quarkiverse.langchain4j.audit.LLMInteractionCompleteEvent;
import io.quarkiverse.langchain4j.audit.LLMInteractionFailureEvent;
import io.quarkiverse.langchain4j.audit.ResponseFromLLMReceivedEvent;
import io.quarkiverse.langchain4j.audit.ToolExecutedEvent;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.response.ResponseAugmenterParams;
import io.quarkiverse.langchain4j.runtime.ContextLocals;
import io.quarkiverse.langchain4j.runtime.QuarkusServiceOutputParser;
import io.quarkiverse.langchain4j.runtime.ResponseSchemaUtil;
import io.quarkiverse.langchain4j.runtime.types.TypeUtil;
import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.quarkus.arc.Arc;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.operators.AbstractMulti;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.vertx.core.Context;

/**
 * Provides the basic building blocks that the generated Interface methods call into
 */
public class AiServiceMethodImplementationSupport {

    private static final Logger log = Logger.getLogger(AiServiceMethodImplementationSupport.class);
    private static final int DEFAULT_MAX_SEQUENTIAL_TOOL_EXECUTIONS = 10;
    private static final List<DefaultMemoryIdProvider> DEFAULT_MEMORY_ID_PROVIDERS;

    private static final ServiceOutputParser SERVICE_OUTPUT_PARSER = new QuarkusServiceOutputParser(); // TODO: this might need to be improved

    static {
        var defaultMemoryIdProviders = ServiceHelper.loadFactories(
                DefaultMemoryIdProvider.class);
        if (defaultMemoryIdProviders.isEmpty()) {
            DEFAULT_MEMORY_ID_PROVIDERS = Collections.emptyList();
        } else {
            DEFAULT_MEMORY_ID_PROVIDERS = new ArrayList<>(defaultMemoryIdProviders);
            DEFAULT_MEMORY_ID_PROVIDERS.sort(new Comparator<>() {
                @Override
                public int compare(DefaultMemoryIdProvider o1, DefaultMemoryIdProvider o2) {
                    return Integer.compare(o1.priority(), o2.priority());
                }
            });
        }
    }

    /**
     * This method is called by the implementations of each ai service method.
     */
    public Object implement(Input input) {
        if (ContextLocals.duplicatedContextActive()) {
            ContextLocals.put(AiServiceConstants.AI_SERVICE_CLASS_NAME, input.context.aiServiceClass.getName());
            ContextLocals.put(AiServiceConstants.AI_SERVICE_METHODNAME, input.createInfo.getMethodName());
        }

        QuarkusAiServiceContext context = input.context;
        AiServiceMethodCreateInfo createInfo = input.createInfo;
        Object[] methodArgs = input.methodArgs;

        var auditSourceInfo = new AuditSourceInfoImpl(createInfo, methodArgs);
        var beanManager = Arc.container().beanManager();

        // TODO: add validation
        try {
            var result = doImplement(createInfo, methodArgs, context, auditSourceInfo);

            beanManager.getEvent().select(LLMInteractionCompleteEvent.class)
                    .fire(new LLMInteractionCompleteEvent(auditSourceInfo, result));

            return result;
        } catch (Exception e) {
            beanManager.getEvent().select(LLMInteractionFailureEvent.class)
                    .fire(new LLMInteractionFailureEvent(auditSourceInfo, e));

            throw e;
        }
    }

    private static Object doImplement(AiServiceMethodCreateInfo methodCreateInfo, Object[] methodArgs,
            QuarkusAiServiceContext context, AuditSourceInfo auditSourceInfo) {
        boolean isRunningOnWorkerThread = !Context.isOnEventLoopThread();
        Object memoryId = memoryId(methodCreateInfo, methodArgs, context.chatMemoryProvider != null);
        Optional<SystemMessage> systemMessage = prepareSystemMessage(methodCreateInfo, methodArgs,
                context.hasChatMemory() ? context.chatMemory(memoryId).messages() : Collections.emptyList());

        boolean supportsJsonSchema = supportsJsonSchema(context);

        UserMessage userMessage = prepareUserMessage(context, methodCreateInfo, methodArgs, supportsJsonSchema);
        Map<String, Object> templateVariables = getTemplateVariables(methodArgs, methodCreateInfo.getUserMessageInfo());

        Type returnType = methodCreateInfo.getReturnType();
        if (TypeUtil.isImage(returnType) || TypeUtil.isResultImage(returnType)) {
            return doImplementGenerateImage(methodCreateInfo, context, systemMessage, userMessage, memoryId, returnType,
                    templateVariables, auditSourceInfo);
        }

        var beanManager = Arc.container().beanManager();
        beanManager.getEvent().select(InitialMessagesCreatedEvent.class)
                .fire(new InitialMessagesCreatedEvent(auditSourceInfo, systemMessage, userMessage));

        boolean needsMemorySeed = needsMemorySeed(context, memoryId); // we need to know figure this out before we add the system and user message

        boolean hasMethodSpecificTools = methodCreateInfo.getToolClassNames() != null
                && !methodCreateInfo.getToolClassNames().isEmpty();
        List<ToolSpecification> toolSpecifications = hasMethodSpecificTools ? methodCreateInfo.getToolSpecifications()
                : context.toolService.toolSpecifications();
        Map<String, ToolExecutor> toolExecutors = hasMethodSpecificTools ? methodCreateInfo.getToolExecutors()
                : context.toolService.toolExecutors();

        if (context.toolService.toolProvider() != null) {
            toolSpecifications = new ArrayList<>();
            toolExecutors = new HashMap<>();
            ToolProviderRequest request = new ToolProviderRequest(memoryId, userMessage);
            ToolProviderResult result = context.toolService.toolProvider().provideTools(request);
            for (ToolSpecification specification : result.tools().keySet()) {
                toolSpecifications.add(specification);
                toolExecutors.put(specification.name(), result.tools().get(specification));
            }
        }
        List<ToolSpecification> effectiveToolSpecifications = toolSpecifications;
        Map<String, ToolExecutor> finalToolExecutors = toolExecutors;

        AugmentationResult augmentationResult = null;
        if (context.retrievalAugmentor != null) {
            List<ChatMessage> chatMemory = context.hasChatMemory()
                    ? context.chatMemory(memoryId).messages()
                    : null;
            Metadata metadata = Metadata.from(userMessage, memoryId, chatMemory);
            AugmentationRequest augmentationRequest = new AugmentationRequest(userMessage, metadata);

            if (!TypeUtil.isMulti(returnType)) {
                augmentationResult = context.retrievalAugmentor.augment(augmentationRequest);
                userMessage = (UserMessage) augmentationResult.chatMessage();
            } else {
                // TODO duplicated context propagation.
                // this a special case where we can't block, so we need to delegate the retrieval augmentation to a worker pool
                CompletableFuture<AugmentationResult> augmentationResultCF = CompletableFuture.supplyAsync(new Supplier<>() {
                    @Override
                    public AugmentationResult get() {
                        return context.retrievalAugmentor.augment(augmentationRequest);
                    }
                }, Infrastructure.getDefaultWorkerPool());

                return Multi.createFrom().completionStage(augmentationResultCF).flatMap(
                        new Function<>() {
                            @Override
                            public Flow.Publisher<?> apply(AugmentationResult ar) {
                                ChatMessage augmentedUserMessage = ar.chatMessage();

                                ChatMemory memory = context.chatMemory(memoryId);
                                UserMessage guardrailsMessage = GuardrailsSupport.invokeInputGuardrails(methodCreateInfo,
                                        (UserMessage) augmentedUserMessage,
                                        memory, ar, templateVariables);
                                List<ChatMessage> messagesToSend = messagesToSend(guardrailsMessage, needsMemorySeed);
                                var stream = new TokenStreamMulti(messagesToSend, effectiveToolSpecifications,
                                        finalToolExecutors, ar.contents(), context, memoryId,
                                        methodCreateInfo.isSwitchToWorkerThreadForToolExecution(), isRunningOnWorkerThread);
                                return stream.plug(m -> ResponseAugmenterSupport.apply(m, methodCreateInfo,
                                        new ResponseAugmenterParams((UserMessage) augmentedUserMessage,
                                                memory, ar, methodCreateInfo.getUserMessageTemplate(),
                                                templateVariables)));
                            }

                            private List<ChatMessage> messagesToSend(UserMessage augmentedUserMessage,
                                    boolean needsMemorySeed) {
                                return context.hasChatMemory()
                                        ? createMessagesToSendForExistingMemory(systemMessage, augmentedUserMessage,
                                                context.chatMemory(memoryId), needsMemorySeed, context, methodCreateInfo)
                                        : createMessagesToSendForNoMemory(systemMessage, augmentedUserMessage,
                                                needsMemorySeed, context, methodCreateInfo);
                            }
                        });
            }
        }

        userMessage = GuardrailsSupport.invokeInputGuardrails(methodCreateInfo, userMessage,
                context.hasChatMemory() ? context.chatMemory(memoryId) : null,
                augmentationResult, templateVariables);

        CommittableChatMemory chatMemory;
        List<ChatMessage> messagesToSend;

        if (context.hasChatMemory()) {
            // we want to defer saving the new messages because the service could fail and be retried
            chatMemory = new DefaultCommittableChatMemory(context.chatMemory(memoryId));
            messagesToSend = createMessagesToSendForExistingMemory(systemMessage, userMessage, chatMemory, needsMemorySeed,
                    context, methodCreateInfo);
        } else {
            chatMemory = new NoopChatMemory();
            messagesToSend = createMessagesToSendForNoMemory(systemMessage, userMessage, needsMemorySeed, context,
                    methodCreateInfo);
        }

        if (TypeUtil.isTokenStream(returnType)) {
            // TODO Indicate the output guardrails cannot be used when using token stream.
            chatMemory.commit(); // for streaming cases, we really have to commit because all alternatives are worse
            return new AiServiceTokenStream(messagesToSend, toolSpecifications, toolExecutors,
                    (augmentationResult != null ? augmentationResult.contents() : null), context, memoryId);
        }

        var actualAugmentationResult = augmentationResult;
        var actualUserMessage = userMessage;
        if (TypeUtil.isMulti(returnType)) {
            chatMemory.commit(); // for streaming cases, we really have to commit because all alternatives are worse
            if (methodCreateInfo.getOutputGuardrailsClassNames().isEmpty()) {
                var stream = new TokenStreamMulti(messagesToSend, toolSpecifications, toolExecutors,
                        (augmentationResult != null ? augmentationResult.contents() : null), context, memoryId,
                        methodCreateInfo.isSwitchToWorkerThreadForToolExecution(), isRunningOnWorkerThread);
                return stream.plug(m -> ResponseAugmenterSupport.apply(m, methodCreateInfo,
                        new ResponseAugmenterParams(actualUserMessage,
                                chatMemory, actualAugmentationResult, methodCreateInfo.getUserMessageTemplate(),
                                Collections.unmodifiableMap(templateVariables))));
            }

            return new TokenStreamMulti(messagesToSend, toolSpecifications, toolExecutors,
                    (augmentationResult != null ? augmentationResult.contents() : null), context, memoryId,
                    methodCreateInfo.isSwitchToWorkerThreadForToolExecution(), isRunningOnWorkerThread)
                    .plug(s -> GuardrailsSupport.accumulate(s, methodCreateInfo))
                    .map(chunk -> {
                        OutputGuardrailResult result;
                        try {
                            result = GuardrailsSupport.invokeOutputGuardrailsForStream(methodCreateInfo,
                                    new OutputGuardrailParams(AiMessage.from(chunk), chatMemory, actualAugmentationResult,
                                            methodCreateInfo.getUserMessageTemplate(),
                                            Collections.unmodifiableMap(templateVariables)));
                        } catch (Exception e) {
                            throw new GuardrailException(e.getMessage(), e);
                        }

                        if (!result.isSuccess()) {
                            if (!result.isRetry()) {
                                throw new GuardrailException(result.toString(), result.getFirstFailureException());
                            } else if (result.getReprompt() != null) {
                                chatMemory.add(new UserMessage(result.getReprompt()));
                                throw new GuardrailsSupport.GuardrailRetryException();
                            } else {
                                // Retry without re-prompting
                                throw new GuardrailsSupport.GuardrailRetryException();
                            }
                        } else {
                            if (result.hasRewrittenResult()) {
                                throw new GuardrailException(
                                        "Attempting to rewrite the LLM output while streaming is not allowed");
                            }
                            return chunk;
                        }
                    })
                    // Retry logic:
                    // 1. retry only on the custom RetryException
                    // 2. If we still have a RetryException afterward, we fail.
                    .onFailure(GuardrailsSupport.GuardrailRetryException.class).retry()
                    .atMost(methodCreateInfo.getGuardrailsMaxRetry())
                    .onFailure(GuardrailsSupport.GuardrailRetryException.class)
                    .transform(t -> new GuardrailException(
                            "Output validation failed. The guardrails have reached the maximum number of retries"))
                    .plug(m -> ResponseAugmenterSupport.apply(m, methodCreateInfo,
                            new ResponseAugmenterParams(actualUserMessage,
                                    chatMemory, actualAugmentationResult, methodCreateInfo.getUserMessageTemplate(),
                                    Collections.unmodifiableMap(templateVariables))));
        }

        Future<Moderation> moderationFuture = triggerModerationIfNeeded(context, methodCreateInfo, messagesToSend);

        log.debug("Attempting to obtain AI response");

        var response = executeRequest(context, methodCreateInfo, messagesToSend, toolSpecifications);

        log.debug("AI response obtained");

        beanManager.getEvent().select(ResponseFromLLMReceivedEvent.class)
                .fire(new ResponseFromLLMReceivedEvent(auditSourceInfo, response));

        TokenUsage tokenUsageAccumulator = response.tokenUsage();

        verifyModerationIfNeeded(moderationFuture);

        int maxSequentialToolExecutions = getMaxSequentialToolExecutions();
        int executionsLeft = maxSequentialToolExecutions;
        while (true) {

            if (executionsLeft-- == 0) {
                throw runtime("Something is wrong, exceeded %s sequential tool executions", maxSequentialToolExecutions);
            }

            AiMessage aiMessage = response.content();
            chatMemory.add(aiMessage);

            if (!aiMessage.hasToolExecutionRequests()) {
                break;
            }

            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                log.debugv("Attempting to execute tool {0}", toolExecutionRequest);
                ToolExecutor toolExecutor = toolExecutors.get(toolExecutionRequest.name());
                if (toolExecutor == null) {
                    throw runtime("Tool executor %s not found", toolExecutionRequest.name());
                }
                String toolExecutionResult = toolExecutor.execute(toolExecutionRequest, memoryId);
                log.debugv("Result of {0} is '{1}'", toolExecutionRequest, toolExecutionResult);
                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(
                        toolExecutionRequest,
                        toolExecutionResult);

                beanManager.getEvent().select(ToolExecutedEvent.class)
                        .fire(new ToolExecutedEvent(auditSourceInfo, toolExecutionRequest, toolExecutionResult));

                chatMemory.add(toolExecutionResultMessage);
            }

            log.debug("Attempting to obtain AI response");
            response = context.chatModel.generate(chatMemory.messages(), toolSpecifications);
            log.debug("AI response obtained");

            beanManager.getEvent().select(ResponseFromLLMReceivedEvent.class)
                    .fire(new ResponseFromLLMReceivedEvent(auditSourceInfo, response));

            tokenUsageAccumulator = sum(tokenUsageAccumulator, response.tokenUsage());
        }

        String userMessageTemplate = methodCreateInfo.getUserMessageTemplate();

        response = GuardrailsSupport.invokeOutputGuardrails(methodCreateInfo, chatMemory, context.chatModel, response,
                toolSpecifications,
                new OutputGuardrailParams(response.content(), chatMemory, augmentationResult, userMessageTemplate,
                        Collections.unmodifiableMap(templateVariables)));

        // everything worked as expected so let's commit the messages
        chatMemory.commit();

        Object guardrailResult = response.metadata().get(OutputGuardrailResult.class.getName());
        if (guardrailResult != null && TypeUtil.isTypeOf(returnType, guardrailResult.getClass())) {
            return guardrailResult;
        }

        response = Response.from(response.content(), tokenUsageAccumulator, response.finishReason(), response.metadata());

        var responseAugmenterParam = new ResponseAugmenterParams(userMessage, chatMemory, augmentationResult,
                userMessageTemplate, templateVariables);
        if (TypeUtil.isResult(returnType)) {
            var parsedResponse = SERVICE_OUTPUT_PARSER.parse(response,
                    TypeUtil.resultTypeParam((ParameterizedType) returnType));
            parsedResponse = ResponseAugmenterSupport.invoke(parsedResponse, methodCreateInfo, responseAugmenterParam);
            return Result.builder()
                    .content(parsedResponse)
                    .tokenUsage(tokenUsageAccumulator)
                    .sources(augmentationResult == null ? null : augmentationResult.contents())
                    .finishReason(response.finishReason())
                    .build();
        }

        return ResponseAugmenterSupport.invoke(SERVICE_OUTPUT_PARSER.parse(response, returnType),
                methodCreateInfo, responseAugmenterParam);
    }

    private static Response<AiMessage> executeRequest(JsonSchema jsonSchema, List<ChatMessage> messagesToSend,
            ChatLanguageModel chatModel, List<ToolSpecification> toolSpecifications) {
        var chatRequest = ChatRequest.builder()
                .messages(messagesToSend)
                .toolSpecifications(toolSpecifications)
                .responseFormat(
                        ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(jsonSchema)
                                .build())
                .build();

        var response = chatModel.chat(chatRequest);

        return new Response<>(
                response.aiMessage(),
                response.tokenUsage(),
                response.finishReason());
    }

    private static Response<AiMessage> executeRequest(List<ChatMessage> messagesToSend, ChatLanguageModel chatModel,
            List<ToolSpecification> toolSpecifications) {
        return (toolSpecifications == null) ? chatModel.generate(messagesToSend)
                : chatModel.generate(messagesToSend, toolSpecifications);
    }

    static Response<AiMessage> executeRequest(AiServiceMethodCreateInfo methodCreateInfo, List<ChatMessage> messagesToSend,
            ChatLanguageModel chatModel, List<ToolSpecification> toolSpecifications) {
        var jsonSchema = supportsJsonSchema(chatModel) ? methodCreateInfo.getResponseSchemaInfo().structuredOutputSchema()
                : Optional.<JsonSchema> empty();

        return jsonSchema.isPresent() ? executeRequest(jsonSchema.get(), messagesToSend, chatModel, toolSpecifications)
                : executeRequest(messagesToSend, chatModel, toolSpecifications);
    }

    static Response<AiMessage> executeRequest(QuarkusAiServiceContext context, AiServiceMethodCreateInfo methodCreateInfo,
            List<ChatMessage> messagesToSend, List<ToolSpecification> toolSpecifications) {
        return executeRequest(methodCreateInfo, messagesToSend, context.chatModel, toolSpecifications);
    }

    private static Object doImplementGenerateImage(AiServiceMethodCreateInfo methodCreateInfo, QuarkusAiServiceContext context,
            Optional<SystemMessage> systemMessage, UserMessage userMessage,
            Object memoryId, Type returnType, Map<String, Object> templateVariables, AuditSourceInfo auditSourceInfo) {
        String imagePrompt;
        if (systemMessage.isPresent()) {
            imagePrompt = systemMessage.get().text() + "\n" + userMessage.singleText();
        } else {
            imagePrompt = userMessage.singleText();
        }

        var beanManager = Arc.container().beanManager();

        beanManager.getEvent().select(InitialMessagesCreatedEvent.class)
                .fire(new InitialMessagesCreatedEvent(auditSourceInfo, systemMessage, userMessage));

        // TODO: does it make sense to use the retrievalAugmentor here? What good would be for us telling the LLM to use this or that information to create an image?
        AugmentationResult augmentationResult = null;

        // TODO: we can only support input guardrails for now as it is tied to AiMessage
        GuardrailsSupport.invokeInputGuardrails(methodCreateInfo, userMessage,
                context.hasChatMemory() ? context.chatMemory(memoryId) : null,
                augmentationResult, templateVariables);

        Response<Image> imageResponse = context.imageModel.generate(imagePrompt);

        beanManager.getEvent().select(LLMInteractionCompleteEvent.class)
                .fire(new LLMInteractionCompleteEvent(auditSourceInfo, imageResponse.content()));

        if (TypeUtil.isImage(returnType)) {
            return imageResponse.content();
        } else if (TypeUtil.isResultImage(returnType)) {
            return Result.builder()
                    .content(imageResponse)
                    .tokenUsage(imageResponse.tokenUsage())
                    .sources(augmentationResult == null ? null : augmentationResult.contents())
                    .finishReason(imageResponse.finishReason())
                    .build();
        } else {
            throw new IllegalStateException("Unsupported return type: " + returnType);
        }
    }

    private static boolean needsMemorySeed(QuarkusAiServiceContext context, Object memoryId) {
        if (context.chatMemorySeeder == null) {
            return false;
        }

        if (!context.hasChatMemory()) {
            return false;
        }

        ChatMemory chatMemory = context.chatMemory(memoryId);
        // if the chat memory is not empty, so we don't seed it
        return chatMemory.messages().isEmpty();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static List<ChatMessage> createMessagesToSendForExistingMemory(Optional<SystemMessage> systemMessage,
            ChatMessage userMessage,
            ChatMemory chatMemory,
            boolean needsMemorySeed,
            QuarkusAiServiceContext context,
            AiServiceMethodCreateInfo methodCreateInfo) {
        if (systemMessage.isPresent()) {
            chatMemory.add(systemMessage.get());
        }

        if (needsMemorySeed) {
            // the seed messages always need to come after the system message and before the user message
            List<ChatMessage> seedChatMessages = context.chatMemorySeeder
                    .seed(new ChatMemorySeeder.Context(methodCreateInfo.getMethodName()));
            for (ChatMessage seedChatMessage : seedChatMessages) {
                chatMemory.add(seedChatMessage);
            }
        }

        chatMemory.add(userMessage);
        return chatMemory.messages();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static List<ChatMessage> createMessagesToSendForNoMemory(Optional<SystemMessage> systemMessage,
            ChatMessage userMessage,
            boolean needsMemorySeed,
            QuarkusAiServiceContext context,
            AiServiceMethodCreateInfo methodCreateInfo) {
        List<ChatMessage> result = new ArrayList<>();
        if (systemMessage.isPresent()) {
            result.add(systemMessage.get());
        }
        if (needsMemorySeed) {
            result.addAll(context.chatMemorySeeder
                    .seed(new ChatMemorySeeder.Context(methodCreateInfo.getMethodName())));
        }
        result.add(userMessage);
        return result;
    }

    private static boolean supportsJsonSchema(ChatLanguageModel chatModel) {
        return (chatModel != null) && chatModel.supportedCapabilities().contains(RESPONSE_FORMAT_JSON_SCHEMA);
    }

    private static boolean supportsJsonSchema(AiServiceContext context) {
        return supportsJsonSchema(context.chatModel);
    }

    private static Future<Moderation> triggerModerationIfNeeded(AiServiceContext context,
            AiServiceMethodCreateInfo createInfo,
            List<ChatMessage> messages) {
        Future<Moderation> moderationFuture = null;
        if (createInfo.isRequiresModeration()) {
            log.debug("Moderation is required and it will be executed in the background");

            // TODO: don't occupy a worker thread for this and instead use the reactive API provided by the client

            ExecutorService defaultExecutor = (ExecutorService) Infrastructure.getDefaultExecutor();
            moderationFuture = defaultExecutor.submit(new Callable<>() {
                @Override
                public Moderation call() {
                    List<ChatMessage> messagesToModerate = removeToolMessages(messages);
                    log.debug("Attempting to moderate messages");
                    var result = context.moderationModel.moderate(messagesToModerate).content();
                    log.debug("Moderation completed");
                    return result;
                }
            });
        }
        return moderationFuture;
    }

    private static Optional<SystemMessage> prepareSystemMessage(AiServiceMethodCreateInfo createInfo, Object[] methodArgs,
            List<ChatMessage> previousChatMessages) {
        if (createInfo.getSystemMessageInfo().isEmpty()) {
            return Optional.empty();
        }
        AiServiceMethodCreateInfo.TemplateInfo systemMessageInfo = createInfo.getSystemMessageInfo().get();
        Map<String, Object> templateParams = new HashMap<>();
        Map<String, Integer> nameToParamPosition = systemMessageInfo.nameToParamPosition();
        for (var entry : nameToParamPosition.entrySet()) {
            templateParams.put(entry.getKey(), methodArgs[entry.getValue()]);
        }

        templateParams.put(ResponseSchemaUtil.templateParam(), createInfo.getResponseSchemaInfo().outputFormatInstructions());
        templateParams.put("chat_memory", previousChatMessages);
        Prompt prompt = PromptTemplate.from(systemMessageInfo.text().get()).apply(templateParams);
        return Optional.of(prompt.toSystemMessage());
    }

    private static UserMessage prepareUserMessage(AiServiceContext context, AiServiceMethodCreateInfo createInfo,
            Object[] methodArgs, boolean supportsJsonSchema) {
        AiServiceMethodCreateInfo.UserMessageInfo userMessageInfo = createInfo.getUserMessageInfo();

        String userName = null;
        ImageContent imageContent = null;
        if (userMessageInfo.userNameParamPosition().isPresent()) {
            userName = methodArgs[userMessageInfo.userNameParamPosition().get()]
                    .toString(); // LangChain4j does this, but might want to make anything other than a String a build time error
        }
        if (userMessageInfo.imageParamPosition().isPresent()) {
            Object imageParamValue = methodArgs[userMessageInfo.imageParamPosition().get()];
            if (imageParamValue instanceof String s) {
                imageContent = ImageContent.from(s);
            } else if (imageParamValue instanceof URI u) {
                imageContent = ImageContent.from(u);
            } else if (imageParamValue instanceof URL u) {
                try {
                    imageContent = ImageContent.from(u.toURI());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            } else if (imageParamValue instanceof Image i) {
                imageContent = ImageContent.from(i);
            } else {
                throw new IllegalStateException("Unsupported parameter type '" + imageParamValue.getClass()
                        + "' annotated with @ImageUrl. Offending AiService is '" + createInfo.getInterfaceName() + "#"
                        + createInfo.getMethodName());
            }
        }

        if (userMessageInfo.template().isPresent()) {
            AiServiceMethodCreateInfo.TemplateInfo templateInfo = userMessageInfo.template().get();
            Map<String, Object> templateVariables = getTemplateVariables(methodArgs, userMessageInfo);
            String templateText;
            if (templateInfo.text().isPresent()) {
                templateText = templateInfo.text().get();
            } else {
                templateText = (String) methodArgs[templateInfo.methodParamPosition().get()];
            }

            boolean hasResponseSchema = createInfo.getResponseSchemaInfo().isInUserMessage().orElse(false)
                    || hasResponseSchema(templateText);

            if (hasResponseSchema && !createInfo.getResponseSchemaInfo().enabled()) {
                throw new RuntimeException(
                        "The %s placeholder cannot be used if the property quarkus.langchain4j.response-schema is set to false. Found in: %s"
                                .formatted(ResponseSchemaUtil.placeholder(), createInfo.getInterfaceName()));
            }

            if (createInfo.getResponseSchemaInfo().enabled()) {
                // No response schema placeholder found in the @SystemMessage and @UserMessage, concat it to the UserMessage.
                if (!createInfo.getResponseSchemaInfo().isInSystemMessage() && !hasResponseSchema && !supportsJsonSchema) {
                    templateText = templateText.concat(ResponseSchemaUtil.placeholder());
                }

                templateVariables.put(ResponseSchemaUtil.templateParam(),
                        createInfo.getResponseSchemaInfo().outputFormatInstructions());
            }

            Prompt prompt = PromptTemplate.from(templateText).apply(templateVariables);
            return createUserMessage(userName, imageContent, prompt.text());

        } else if (userMessageInfo.paramPosition().isPresent()) {
            Integer paramIndex = userMessageInfo.paramPosition().get();
            Object argValue = methodArgs[paramIndex];
            if (argValue == null) {
                throw new IllegalArgumentException(
                        "Unable to construct UserMessage for class '" + context.aiServiceClass.getName()
                                + "' because parameter with index "
                                + paramIndex + " is null");
            }

            String text = toString(argValue);
            return createUserMessage(userName, imageContent,
                    text.concat(supportsJsonSchema || !createInfo.getResponseSchemaInfo().enabled() ? ""
                            : createInfo.getResponseSchemaInfo().outputFormatInstructions()));
        } else {
            throw new IllegalStateException("Unable to construct UserMessage for class '" + context.aiServiceClass.getName()
                    + "'. Please contact the maintainers");
        }
    }

    private static Map<String, Object> getTemplateVariables(Object[] methodArgs,
            AiServiceMethodCreateInfo.UserMessageInfo userMessageInfo) {
        Map<String, Object> variables = new HashMap<>();

        if (userMessageInfo.template().isPresent()) {
            AiServiceMethodCreateInfo.TemplateInfo templateInfo = userMessageInfo.template().get();
            Map<String, Integer> nameToParamPosition = templateInfo.nameToParamPosition();

            for (var entry : nameToParamPosition.entrySet()) {
                Object value = transformTemplateParamValue(methodArgs[entry.getValue()]);
                variables.put(entry.getKey(), value);
            }
        }

        return variables;
    }

    private static UserMessage createUserMessage(String name, ImageContent imageContent, String text) {
        if (name == null) {
            if (imageContent == null) {
                return userMessage(text);
            } else {
                return UserMessage.userMessage(List.of(TextContent.from(text), imageContent));
            }
        } else {
            if (imageContent == null) {
                return userMessage(name, text);
            } else {
                return userMessage(name, List.of(TextContent.from(text), imageContent));
            }
        }
    }

    private static Object transformTemplateParamValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value.getClass().isArray()) {
            // Qute does not transform these values but LangChain4j expects to be converted to a [item1, item2, item3] like syntax
            return Arrays.toString((Object[]) value);
        }
        return value;
    }

    private static Object memoryId(AiServiceMethodCreateInfo createInfo, Object[] methodArgs, boolean hasChatMemoryProvider) {
        if (createInfo.getMemoryIdParamPosition().isPresent()) {
            return methodArgs[createInfo.getMemoryIdParamPosition().get()];
        }

        if (hasChatMemoryProvider) {
            for (DefaultMemoryIdProvider provider : DEFAULT_MEMORY_ID_PROVIDERS) {
                Object memoryId = provider.getMemoryId();
                if (memoryId != null) {
                    String perServiceSuffix = "#" + createInfo.getInterfaceName() + "." + createInfo.getMethodName();
                    return memoryId + perServiceSuffix;
                }
            }
        }

        // fallback to the default since there is nothing else we can really use here
        return "default";
    }

    // TODO: share these methods with LangChain4j

    private static String toString(Object arg) {
        if (arg.getClass().isArray()) {
            return arrayToString(arg);
        } else if (arg.getClass().isAnnotationPresent(StructuredPrompt.class)) {
            return StructuredPromptProcessor.toPrompt(arg).text();
        } else {
            return arg.toString();
        }
    }

    private static String arrayToString(Object arg) {
        StringBuilder sb = new StringBuilder("[");
        int length = Array.getLength(arg);
        for (int i = 0; i < length; i++) {
            sb.append(toString(Array.get(arg, i)));
            if (i < length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static int getMaxSequentialToolExecutions() {
        return ConfigProvider.getConfig().getOptionalValue("quarkus.langchain4j.ai-service.max-tool-executions", Integer.class)
                .orElse(
                        DEFAULT_MAX_SEQUENTIAL_TOOL_EXECUTIONS);
    }

    public static class Input {
        final QuarkusAiServiceContext context;
        final AiServiceMethodCreateInfo createInfo;
        final Object[] methodArgs;

        public Input(QuarkusAiServiceContext context, AiServiceMethodCreateInfo createInfo, Object[] methodArgs) {
            this.context = context;
            this.createInfo = createInfo;
            this.methodArgs = methodArgs;
        }
    }

    public interface Wrapper {

        Object wrap(Input input, Function<Input, Object> fun);
    }

    private static class TokenStreamMulti extends AbstractMulti<String> implements Multi<String> {
        private final List<ChatMessage> messagesToSend;
        private final List<ToolSpecification> toolSpecifications;
        private final Map<String, ToolExecutor> toolsExecutors;
        private final List<Content> contents;
        private final QuarkusAiServiceContext context;
        private final Object memoryId;
        private final boolean switchToWorkerThreadForToolExecution;
        private final boolean isCallerRunningOnWorkerThread;

        public TokenStreamMulti(List<ChatMessage> messagesToSend, List<ToolSpecification> toolSpecifications,
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
                    .onNext(processor::onNext)
                    .onComplete(message -> processor.onComplete())
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
}
