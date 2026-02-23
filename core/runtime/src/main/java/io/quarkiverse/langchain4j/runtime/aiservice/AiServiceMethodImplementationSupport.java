package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.output.TokenUsage.sum;
import static dev.langchain4j.service.AiServices.removeToolMessages;
import static dev.langchain4j.service.AiServices.verifyModerationIfNeeded;
import static io.quarkiverse.langchain4j.runtime.ResponseSchemaUtil.hasResponseSchema;
import static java.util.Objects.nonNull;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.exception.ToolArgumentsException;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.observability.api.event.AiServiceResponseReceivedEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServiceTokenStream;
import dev.langchain4j.service.AiServiceTokenStreamParameters;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.spi.ServiceHelper;
import io.quarkiverse.langchain4j.AudioUrl;
import io.quarkiverse.langchain4j.ImageUrl;
import io.quarkiverse.langchain4j.PdfUrl;
import io.quarkiverse.langchain4j.VideoUrl;
import io.quarkiverse.langchain4j.response.ResponseAugmenterParams;
import io.quarkiverse.langchain4j.runtime.ContextLocals;
import io.quarkiverse.langchain4j.runtime.PreventsErrorHandlerExecution;
import io.quarkiverse.langchain4j.runtime.QuarkusServiceOutputParser;
import io.quarkiverse.langchain4j.runtime.ResponseSchemaUtil;
import io.quarkiverse.langchain4j.runtime.aiservice.GuardrailsSupport.OutputGuardrailStreamingMapper;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkiverse.langchain4j.runtime.types.TypeSignatureParser;
import io.quarkiverse.langchain4j.runtime.types.TypeUtil;
import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.runtime.BlockingOperationControl;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;

/**
 * Provides the basic building blocks that the generated Interface methods call
 * into
 */
public class AiServiceMethodImplementationSupport {

    private static final Logger log = Logger.getLogger(AiServiceMethodImplementationSupport.class);
    private static final int DEFAULT_MAX_SEQUENTIAL_TOOL_EXECUTIONS = 10;
    private static final List<DefaultMemoryIdProvider> DEFAULT_MEMORY_ID_PROVIDERS;

    private static final ServiceOutputParser SERVICE_OUTPUT_PARSER = new QuarkusServiceOutputParser(); // TODO: this
                                                                                                       // might need to
                                                                                                       // be improved

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

        InvocationContext invocationContext = InvocationContext.builder()
                .invocationId(UUID.randomUUID())
                .interfaceName(context.aiServiceClass.getName())
                .methodName(createInfo.getMethodName())
                .methodArguments((methodArgs != null) ? Arrays.asList(methodArgs) : List.of())
                .chatMemoryId(memoryId(createInfo, methodArgs, context.hasChatMemory()))
                .invocationParameters(findInvocationParams(methodArgs))
                .timestampNow()
                .build();

        // TODO: add validation
        try {
            var result = doImplement(createInfo, invocationContext, context);

            return result;
        } catch (Exception e) {

            // New firing
            context.eventListenerRegistrar.fireEvent(
                    AiServiceErrorEvent.builder()
                            .invocationContext(invocationContext)
                            .error(e)
                            .build());

            throw e;
        }
    }

    private static Object doImplement(AiServiceMethodCreateInfo methodCreateInfo, InvocationContext invocationContext,
            QuarkusAiServiceContext context) {
        if (TypeUtil.isMulti(methodCreateInfo.getReturnType()) && !BlockingOperationControl.isBlockingAllowed()) {
            // this a special case where we can't block, so we need to delegate the to a worker pool
            // as so many of the things done in LangChain4j are blocking
            return Multi.createFrom().deferred(
                    () -> ((Multi<?>) doImplement0(methodCreateInfo, invocationContext, context)))
                    .runSubscriptionOn(createExecutor());
        } else {
            return doImplement0(methodCreateInfo, invocationContext, context);
        }
    }

    private static Object doImplement0(AiServiceMethodCreateInfo methodCreateInfo, InvocationContext invocationContext,
            QuarkusAiServiceContext context) {
        boolean isRunningOnWorkerThread = !Context.isOnEventLoopThread();
        Object[] methodArgs = invocationContext.methodArguments().toArray(Object[]::new);
        Object memoryId = invocationContext.chatMemoryId();

        var chatMemory = context.hasChatMemory() ? context.chatMemoryService.getOrCreateChatMemory(memoryId) : null;
        // we want to defer saving the new messages because the service could fail and be retried
        // this also avoids fetching data from the remote stores every time we ask for the messages
        var committableChatMemory = chatMemory != null ? new DefaultCommittableChatMemory(chatMemory)
                : new NoopChatMemory();

        Optional<SystemMessage> systemMessage = prepareSystemMessage(methodCreateInfo, methodArgs, context, memoryId,
                committableChatMemory);

        boolean supportsJsonSchema = supportsJsonSchema(context, methodCreateInfo, methodArgs);

        UserMessage userMessage = prepareUserMessage(context, methodCreateInfo, methodArgs, supportsJsonSchema);
        Map<String, Object> templateVariables = getTemplateVariables(methodArgs, methodCreateInfo.getUserMessageInfo());

        Type returnType = methodCreateInfo.getReturnType();
        boolean isMulti = TypeUtil.isMulti(returnType);

        final boolean isStringMulti = (isMulti && returnType instanceof ParameterizedType
                && TypeUtil.isTypeOf(((ParameterizedType) returnType).getActualTypeArguments()[0], String.class));
        if (TypeUtil.isImage(returnType) || TypeUtil.isResultImage(returnType)) {
            return doImplementGenerateImage(methodCreateInfo, context, invocationContext, systemMessage, userMessage, memoryId,
                    returnType,
                    templateVariables);
        }

        // New firing
        context.eventListenerRegistrar.fireEvent(
                AiServiceStartedEvent.builder()
                        .invocationContext(invocationContext)
                        .systemMessage(systemMessage)
                        .userMessage(userMessage)
                        .build());

        boolean needsMemorySeed = needsMemorySeed(context, memoryId); // we need to know figure this out before we add
                                                                      // the system and user message

        boolean hasMethodSpecificTools = methodCreateInfo.getToolClassInfo() != null
                && !methodCreateInfo.getToolClassInfo().isEmpty();
        List<ToolSpecification> toolSpecifications = hasMethodSpecificTools ? methodCreateInfo.getToolSpecifications()
                : context.toolService.toolSpecifications();
        Map<String, ToolExecutor> toolExecutors = hasMethodSpecificTools ? methodCreateInfo.getToolExecutors()
                : context.toolService.toolExecutors();
        Set<String> immediateReturnToolNames = Set.of();

        if (context.toolService.toolProvider() != null) {
            toolSpecifications = toolSpecifications != null ? new ArrayList<>(toolSpecifications) : new ArrayList<>();
            toolExecutors = toolExecutors != null ? new HashMap<>(toolExecutors) : new HashMap<>();
            ToolProviderRequest request = new QuarkusToolProviderRequest(memoryId, userMessage,
                    methodCreateInfo.getMcpClientNames());
            ToolProviderResult result = context.toolService.toolProvider().provideTools(request);
            immediateReturnToolNames = Utils.copy(result.immediateReturnToolNames());
            for (ToolSpecification specification : result.tools().keySet()) {
                toolSpecifications.add(specification);
                toolExecutors.put(specification.name(), result.tools().get(specification));
            }
        }

        AugmentationResult augmentationResult = null;
        if (context.retrievalAugmentor != null) {
            Metadata metadata = Metadata.builder()
                    .chatMessage(userMessage)
                    .chatMemory(committableChatMemory.messages())
                    .invocationContext(invocationContext)
                    .build();
            AugmentationRequest augmentationRequest = new AugmentationRequest(userMessage, metadata);

            augmentationResult = context.retrievalAugmentor.augment(augmentationRequest);
            userMessage = (UserMessage) augmentationResult.chatMessage();
        }

        var guardrailService = context.guardrailService();

        var guardrailParams = GuardrailRequestParams.builder()
                .chatMemory(chatMemory)
                .augmentationResult(augmentationResult)
                .userMessageTemplate(methodCreateInfo.getUserMessageTemplate())
                .variables(templateVariables)
                .invocationContext(invocationContext)
                .aiServiceListenerRegistrar(context.eventListenerRegistrar)
                .build();

        userMessage = GuardrailsSupport.executeInputGuardrails(guardrailService, userMessage, methodCreateInfo,
                guardrailParams);

        List<ChatMessage> messagesToSend;
        if (context.hasChatMemory()) {
            messagesToSend = createMessagesToSendForExistingMemory(systemMessage, userMessage, committableChatMemory,
                    needsMemorySeed,
                    context, methodCreateInfo);
        } else {
            messagesToSend = createMessagesToSendForNoMemory(systemMessage, userMessage, needsMemorySeed, context,
                    methodCreateInfo);
        }

        if (TypeUtil.isTokenStream(returnType)) {
            // NOTE - only the quarkus-specific output guardrails aren't implemented using a
            // TokenStream
            // Upstream supports it
            committableChatMemory.commit(); // for streaming cases, we really have to commit because all alternatives
                                            // are worse
            var aiServiceTokenStreamParams = AiServiceTokenStreamParameters.builder()
                    .messages(messagesToSend)
                    .toolSpecifications(toolSpecifications)
                    .toolExecutors(toolExecutors)
                    .retrievedContents((augmentationResult != null ? augmentationResult.contents() : null))
                    .context(context)
                    .invocationContext(InvocationContext.builder().chatMemoryId(memoryId).build())
                    .methodKey(methodCreateInfo)
                    .toolArgumentsErrorHandler((e, c) -> {
                        throw new RuntimeException(e);
                    })
                    .toolExecutionErrorHandler((e, c) -> ToolErrorHandlerResult.text(e.getMessage()))
                    .commonGuardrailParams(
                            GuardrailRequestParams.builder()
                                    .chatMemory(committableChatMemory)
                                    .augmentationResult(augmentationResult)
                                    .userMessageTemplate(methodCreateInfo.getUserMessageTemplate())
                                    .variables(templateVariables)
                                    .invocationContext(invocationContext)
                                    .aiServiceListenerRegistrar(context.eventListenerRegistrar)
                                    .build())
                    .build();
            return new AiServiceTokenStream(aiServiceTokenStreamParams);
        }

        var actualAugmentationResult = augmentationResult;
        var actualUserMessage = userMessage;

        if (isMulti) {
            committableChatMemory.commit(); // for streaming cases, we really have to commit because all alternatives
                                            // are worse
            var hasUpstreamGuardrails = methodCreateInfo.getOutputGuardrails().hasGuardrails();
            Multi<?> stream = new TokenStreamMulti(messagesToSend, toolSpecifications, toolExecutors,
                    (augmentationResult != null ? augmentationResult.contents() : null), context, invocationContext, memoryId,
                    methodCreateInfo.isSwitchToWorkerThreadForToolExecution(), isRunningOnWorkerThread,
                    methodCreateInfo, methodArgs);

            if (hasUpstreamGuardrails) {
                stream = stream.filter(o -> o instanceof ChatEvent)
                        .map(ChatEvent.class::cast)
                        .plug(s -> GuardrailsSupport.accumulate(s, methodCreateInfo))
                        .map(
                                new OutputGuardrailStreamingMapper(
                                        guardrailService,
                                        methodCreateInfo,
                                        GuardrailRequestParams.builder()
                                                .chatMemory(committableChatMemory)
                                                .augmentationResult(augmentationResult)
                                                .userMessageTemplate(methodCreateInfo.getUserMessageTemplate())
                                                .variables(templateVariables)
                                                .invocationContext(invocationContext)
                                                .aiServiceListenerRegistrar(context.eventListenerRegistrar)
                                                .build(),
                                        isStringMulti))
                        .onFailure(GuardrailsSupport::isOutputGuardrailRetry)
                        .retry()
                        .atMost(methodCreateInfo.getOutputGuardrails().getMaxRetriesAsSetByConfig());
            } else {
                stream = stream.filter(event -> !isStringMulti || event instanceof ChatEvent.PartialResponseEvent)
                        .map(event -> {
                            if (isStringMulti && (event instanceof ChatEvent.PartialResponseEvent)) {
                                return ((ChatEvent.PartialResponseEvent) event).getChunk();
                            }

                            return event;
                        });
            }

            return stream.plug(m -> ResponseAugmenterSupport.apply(m, methodCreateInfo,
                    new ResponseAugmenterParams(actualUserMessage, chatMemory, actualAugmentationResult,
                            methodCreateInfo.getUserMessageTemplate(), templateVariables)));
        }

        Future<Moderation> moderationFuture = triggerModerationIfNeeded(context, methodCreateInfo, messagesToSend);

        log.debug("Attempting to obtain AI response");

        ChatRequest chatRequest = context.chatRequestTransformer
                .apply(createChatRequest(context, methodCreateInfo, methodArgs, messagesToSend, toolSpecifications),
                        memoryId);
        ChatExecutor chatExecutor = ChatExecutor.builder(context.effectiveChatModel(methodCreateInfo, methodArgs))
                .chatRequest(chatRequest)
                .invocationContext(invocationContext)
                .eventListenerRegistrar(context.eventListenerRegistrar)
                .build();

        ChatResponse response = chatExecutor.execute();

        log.debug("AI response obtained");

        // New firing
        context.eventListenerRegistrar.fireEvent(
                AiServiceResponseReceivedEvent.builder()
                        .invocationContext(invocationContext)
                        .request(chatRequest)
                        .response(response)
                        .build());

        TokenUsage tokenUsageAccumulator = response.tokenUsage();

        verifyModerationIfNeeded(moderationFuture);

        int maxSequentialToolExecutions = context.maxSequentialToolExecutions != null
                && context.maxSequentialToolExecutions > 0
                        ? context.maxSequentialToolExecutions
                        : getMaxSequentialToolExecutions();
        int executionsLeft = maxSequentialToolExecutions;
        List<ChatResponse> intermediateResponses = new ArrayList<>();
        while (true) {
            if (executionsLeft-- == 0) {
                throw runtime("Something is wrong, exceeded %s sequential tool executions",
                        maxSequentialToolExecutions);
            }

            AiMessage aiMessage = response.aiMessage();
            committableChatMemory.add(aiMessage);

            if (!aiMessage.hasToolExecutionRequests()) {
                break;
            }
            intermediateResponses.add(response);

            boolean immediateToolReturn = true;
            List<ToolExecution> toolExecutions = new ArrayList<>();
            List<ToolExecutionResultMessage> toolResults = new ArrayList<>();
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                log.debugv("Attempting to execute tool {0}", toolExecutionRequest);
                ToolExecutor toolExecutor = toolExecutors.get(toolExecutionRequest.name());

                ToolExecutionResult toolExecutionResult = toolExecutor == null
                        ? context.toolService.applyToolHallucinationStrategy(toolExecutionRequest)
                        : executeTool(toolExecutionRequest, toolExecutor, invocationContext,
                                context.toolService.argumentsErrorHandler(), context.toolService.executionErrorHandler());

                // New firing
                context.eventListenerRegistrar.fireEvent(
                        dev.langchain4j.observability.api.event.ToolExecutedEvent.builder()
                                .invocationContext(invocationContext)
                                .request(toolExecutionRequest)
                                .resultText(toolExecutionResult.resultText())
                                .build());

                ToolExecutionResultMessage toolExecutionResultMessage = ToolExecutionResultMessage.from(toolExecutionRequest,
                        toolExecutionResult.resultText());

                ToolExecution toolExecution = ToolExecution.builder()
                        .request(toolExecutionRequest)
                        .result(toolExecutionResult)
                        .build();
                toolExecutions.add(toolExecution);
                toolResults.add(toolExecutionResultMessage);

                // If any tool does not return immediately, results must be processed by LLM
                if (!isImmediateReturnTool(toolExecutionRequest.name(), toolExecutor, immediateReturnToolNames)) {
                    immediateToolReturn = false;
                }

            }
            for (ToolExecutionResultMessage toolResult : toolResults) {
                committableChatMemory.add(toolResult);
            }
            if (immediateToolReturn) {
                if (!TypeUtil.isResult(returnType)) {
                    throw IllegalConfigurationException
                            .illegalConfiguration("@Tool with IMMEDIATE return behavior must return a Result");
                }
                ChatResponse finalResponse = intermediateResponses.remove(intermediateResponses.size() - 1);
                var result = Result.builder()
                        .content(null)
                        .tokenUsage(tokenUsageAccumulator)
                        .sources(augmentationResult == null ? null : augmentationResult.contents())
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .toolExecutions(toolExecutions)
                        .intermediateResponses(intermediateResponses)
                        .finalResponse(finalResponse)
                        .build();

                context.eventListenerRegistrar.fireEvent(
                        AiServiceCompletedEvent.builder()
                                .invocationContext(invocationContext)
                                .result(result)
                                .build());

                return result;
            }

            log.debug("Attempting to obtain AI response");
            ChatModel effectiveChatModel = context.effectiveChatModel(methodCreateInfo, methodArgs);
            ChatRequest.Builder chatRequestBuilder = ChatRequest.builder().messages(committableChatMemory.messages());
            DefaultChatRequestParameters.Builder<?> parametersBuilder = ChatRequestParameters.builder();
            if (supportsJsonSchema(effectiveChatModel)) {
                Optional<JsonSchema> jsonSchema = methodCreateInfo.getResponseSchemaInfo().structuredOutputSchema();
                if (jsonSchema.isPresent()) {
                    parametersBuilder = constructStructuredResponseParams(toolSpecifications, jsonSchema.get());
                } else {
                    parametersBuilder.toolSpecifications(toolSpecifications);
                }
            } else {
                parametersBuilder.toolSpecifications(toolSpecifications);
            }

            if (nonNull(context.chatModel.defaultRequestParameters())) {
                var toolChoice = context.chatModel.defaultRequestParameters().toolChoice();
                if (nonNull(toolChoice) && toolChoice.equals(ToolChoice.REQUIRED)
                        && !context.allowContinuousForcedToolCalling) {
                    // This code is needed to avoid a infinite-loop when using the AiService
                    // in combination with the tool-choice option set to REQUIRED.
                    // If the tool-choice option is not set to AUTO after calling the tool,
                    // the model may continuously reselect the same tool in subsequent responses,
                    // even though the tool has already been invoked.
                    parametersBuilder.toolChoice(ToolChoice.AUTO);
                }
            }

            ChatRequest request = chatRequestBuilder.parameters(parametersBuilder.build()).build();
            response = effectiveChatModel.chat(request);
            log.debug("AI response obtained");

            // New firing
            context.eventListenerRegistrar.fireEvent(
                    AiServiceResponseReceivedEvent.builder()
                            .invocationContext(invocationContext)
                            .request(request)
                            .response(response)
                            .build());

            tokenUsageAccumulator = sum(tokenUsageAccumulator, response.tokenUsage());
        }

        String userMessageTemplate = methodCreateInfo.getUserMessageTemplate();
        var guardrailResult = GuardrailsSupport.executeOutputGuardrails(guardrailService, methodCreateInfo, response,
                chatExecutor,
                GuardrailRequestParams.builder()
                        .chatMemory(committableChatMemory)
                        .augmentationResult(augmentationResult)
                        .userMessageTemplate(methodCreateInfo.getUserMessageTemplate())
                        .variables(templateVariables)
                        .invocationContext(invocationContext)
                        .aiServiceListenerRegistrar(context.eventListenerRegistrar)
                        .build());

        // everything worked as expected so let's commit the messages
        committableChatMemory.commit();

        var responseAugmenterParam = new ResponseAugmenterParams(userMessage, committableChatMemory, augmentationResult,
                userMessageTemplate, templateVariables);

        if ((guardrailResult != null) && TypeUtil.isTypeOf(returnType, guardrailResult.getClass())) {
            context.eventListenerRegistrar.fireEvent(
                    AiServiceCompletedEvent.builder()
                            .invocationContext(invocationContext)
                            .result(guardrailResult)
                            .build());

            return ResponseAugmenterSupport.invoke(guardrailResult, methodCreateInfo, responseAugmenterParam);
        }

        if (guardrailResult instanceof ChatResponse) {
            response = (ChatResponse) guardrailResult;
        }

        response = ChatResponse.builder().aiMessage(response.aiMessage()).metadata(response.metadata()).build();

        if (TypeUtil.isResult(returnType)) {
            var parsedResponse = SERVICE_OUTPUT_PARSER.parse(
                    ChatResponse.builder().aiMessage(response.aiMessage()).build(),
                    TypeUtil.resultTypeParam((ParameterizedType) returnType));
            parsedResponse = ResponseAugmenterSupport.invoke(parsedResponse, methodCreateInfo, responseAugmenterParam);
            var result = Result.builder()
                    .content(parsedResponse)
                    .tokenUsage(tokenUsageAccumulator)
                    .sources(augmentationResult == null ? null : augmentationResult.contents())
                    .finishReason(response.finishReason())
                    .build();

            context.eventListenerRegistrar.fireEvent(
                    AiServiceCompletedEvent.builder()
                            .invocationContext(invocationContext)
                            .result(result)
                            .build());

            return result;
        }

        var augmentedResponse = ResponseAugmenterSupport.invoke(
                SERVICE_OUTPUT_PARSER.parse(ChatResponse.builder().aiMessage(response.aiMessage()).build(), returnType),
                methodCreateInfo, responseAugmenterParam);

        context.eventListenerRegistrar.fireEvent(
                AiServiceCompletedEvent.builder()
                        .invocationContext(invocationContext)
                        .result(augmentedResponse)
                        .build());

        return augmentedResponse;
    }

    private static InvocationParameters findInvocationParams(Object[] args) {
        if (args == null) {
            return new InvocationParameters();
        }
        for (Object arg : args) {
            if (arg != null && arg instanceof InvocationParameters) {
                return (InvocationParameters) arg;
            }
        }
        return new InvocationParameters();
    }

    private static ToolExecutionResult executeTool(ToolExecutionRequest toolExecutionRequest, ToolExecutor toolExecutor,
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

    private static ChatRequest createChatRequest(JsonSchema jsonSchema, List<ChatMessage> messagesToSend,
            ChatModel chatModel,
            List<ToolSpecification> toolSpecifications) {
        return ChatRequest.builder()
                .messages(messagesToSend)
                .parameters(constructStructuredResponseParams(toolSpecifications, jsonSchema).build())
                .build();
    }

    static ChatRequest createChatRequest(List<ChatMessage> messagesToSend, ChatModel chatModel,
            List<ToolSpecification> toolSpecifications) {
        var chatRequest = ChatRequest.builder()
                .messages(messagesToSend);

        if (toolSpecifications != null) {
            chatRequest.toolSpecifications(toolSpecifications);
        }
        return chatRequest.build();
    }

    static ChatRequest createChatRequest(AiServiceMethodCreateInfo methodCreateInfo, List<ChatMessage> messagesToSend,
            ChatModel chatModel, List<ToolSpecification> toolSpecifications) {
        var jsonSchema = supportsJsonSchema(chatModel)
                ? methodCreateInfo.getResponseSchemaInfo().structuredOutputSchema()
                : Optional.<JsonSchema> empty();

        return jsonSchema.isPresent()
                ? createChatRequest(jsonSchema.get(), messagesToSend, chatModel, toolSpecifications)
                : createChatRequest(messagesToSend, chatModel, toolSpecifications);
    }

    static ChatRequest createChatRequest(QuarkusAiServiceContext context,
            AiServiceMethodCreateInfo methodCreateInfo, Object[] methodArgs,
            List<ChatMessage> messagesToSend, List<ToolSpecification> toolSpecifications) {

        return createChatRequest(methodCreateInfo, messagesToSend,
                context.effectiveChatModel(methodCreateInfo, methodArgs),
                toolSpecifications);
    }

    private static Object doImplementGenerateImage(AiServiceMethodCreateInfo methodCreateInfo,
            QuarkusAiServiceContext context, InvocationContext invocationContext,
            Optional<SystemMessage> systemMessage, UserMessage userMessage,
            Object memoryId, Type returnType, Map<String, Object> templateVariables) {

        // New firing
        context.eventListenerRegistrar.fireEvent(
                AiServiceStartedEvent.builder()
                        .invocationContext(invocationContext)
                        .systemMessage(systemMessage)
                        .userMessage(userMessage)
                        .build());

        // TODO: does it make sense to use the retrievalAugmentor here? What good would
        // be for us telling the LLM to use this or that information to create an
        // image?
        AugmentationResult augmentationResult = null;

        // TODO: we can only support input guardrails for now as it is tied to AiMessage
        var chatMemory = context.hasChatMemory() ? context.chatMemoryService.getChatMemory(memoryId) : null;
        var guardrailParams = GuardrailRequestParams.builder()
                .chatMemory(chatMemory)
                .augmentationResult(augmentationResult)
                .userMessageTemplate(methodCreateInfo.getUserMessageTemplate())
                .variables(templateVariables)
                .invocationContext(invocationContext)
                .aiServiceListenerRegistrar(context.eventListenerRegistrar)
                .build();
        var um = GuardrailsSupport.executeInputGuardrails(context.guardrailService(), userMessage, methodCreateInfo,
                guardrailParams);

        var imagePrompt = systemMessage
                .map(sm -> "%s\n%s".formatted(sm.text(), um.singleText()))
                .orElseGet(um::singleText);

        context.eventListenerRegistrar.fireEvent(
                AiServiceRequestIssuedEvent.builder()
                        .invocationContext(invocationContext)
                        .request(
                                ChatRequest.builder()
                                        .messages(UserMessage.from(imagePrompt))
                                        .build())
                        .build());

        Response<Image> imageResponse = context.imageModel.generate(imagePrompt);

        // New firing
        context.eventListenerRegistrar.fireEvent(
                AiServiceCompletedEvent.builder()
                        .invocationContext(invocationContext)
                        .result(imageResponse.content())
                        .build());

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

        ChatMemory chatMemory = context.chatMemoryService.getChatMemory(memoryId);
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
            // the seed messages always need to come after the system message and before the
            // user message
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

    private static DefaultChatRequestParameters.Builder<?> constructStructuredResponseParams(
            List<ToolSpecification> toolSpecifications, JsonSchema jsonSchema) {
        return ChatRequestParameters.builder()
                .toolSpecifications(toolSpecifications)
                .responseFormat(ResponseFormat.builder().type(JSON).jsonSchema(jsonSchema).build());
    }

    private static boolean supportsJsonSchema(ChatModel chatModel) {
        return (chatModel != null) && chatModel.supportedCapabilities().contains(RESPONSE_FORMAT_JSON_SCHEMA);
    }

    private static boolean supportsJsonSchema(QuarkusAiServiceContext context,
            AiServiceMethodCreateInfo methodCreateInfo,
            Object[] methodArgs) {
        return supportsJsonSchema(context.effectiveChatModel(methodCreateInfo, methodArgs));
    }

    private static Future<Moderation> triggerModerationIfNeeded(QuarkusAiServiceContext context,
            AiServiceMethodCreateInfo createInfo,
            List<ChatMessage> messages) {
        Future<Moderation> moderationFuture = null;
        if (createInfo.isRequiresModeration()) {
            log.debug("Moderation is required and it will be executed in the background");

            // TODO: don't occupy a worker thread for this and instead use the reactive API
            // provided by the client

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

    private static Optional<SystemMessage> prepareSystemMessage(AiServiceMethodCreateInfo createInfo,
            Object[] methodArgs,
            QuarkusAiServiceContext context,
            Object memoryId,
            CommittableChatMemory committableChatMemory) {
        List<ChatMessage> previousChatMessages = committableChatMemory.messages();

        if (createInfo.getSystemMessageInfo().isEmpty()) {
            return context.systemMessageProvider.apply(memoryId).map(SystemMessage::new);
        }
        AiServiceMethodCreateInfo.TemplateInfo systemMessageInfo = createInfo.getSystemMessageInfo().get();
        Map<String, Object> templateParams = new HashMap<>();
        Map<String, Integer> nameToParamPosition = systemMessageInfo.nameToParamPosition();
        for (var entry : nameToParamPosition.entrySet()) {
            if (entry.getKey() != null) {
                templateParams.put(entry.getKey(), methodArgs[entry.getValue()]);
            }
        }

        templateParams.put(ResponseSchemaUtil.templateParam(),
                createInfo.getResponseSchemaInfo().outputFormatInstructions());
        templateParams.put("chat_memory", previousChatMessages);
        Optional<String> maybeText = systemMessageInfo.text();
        if (maybeText.isPresent()) {
            return Optional.of(PromptTemplate.from(maybeText.get()).apply(templateParams).toSystemMessage());
        } else {
            return Optional.empty();
        }
    }

    private static UserMessage prepareUserMessage(AiServiceContext context, AiServiceMethodCreateInfo createInfo,
            Object[] methodArgs, boolean supportsJsonSchema) {
        AiServiceMethodCreateInfo.UserMessageInfo userMessageInfo = createInfo.getUserMessageInfo();

        String userName = null;
        if (userMessageInfo.userNameParamPosition().isPresent()) {
            userName = methodArgs[userMessageInfo.userNameParamPosition().get()]
                    .toString(); // LangChain4j does this, but might want to make anything other than a String a
                                                                                                     // build time error
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
                // No response schema placeholder found in the @SystemMessage and @UserMessage,
                // concat it to the UserMessage.
                if (!createInfo.getResponseSchemaInfo().isInSystemMessage() && !hasResponseSchema
                        && !supportsJsonSchema) {
                    templateText = templateText.concat(ResponseSchemaUtil.placeholder());
                }

                templateVariables.put(ResponseSchemaUtil.templateParam(),
                        createInfo.getResponseSchemaInfo().outputFormatInstructions());
            }

            Prompt prompt = PromptTemplate.from(templateText).apply(templateVariables);
            List<Content> finalContents = new ArrayList<>();
            finalContents.add(TextContent.from(prompt.text()));
            handleSpecialContentTypes(createInfo, methodArgs, finalContents);
            return toUserMessage(userName, finalContents);

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

            List<Content> finalContents = new ArrayList<>();
            finalContents
                    .add(TextContent.from(text.concat(supportsJsonSchema || !createInfo.getResponseSchemaInfo().enabled() ? ""
                            : createInfo.getResponseSchemaInfo().outputFormatInstructions())));
            handleSpecialContentTypes(createInfo, methodArgs, finalContents);
            return toUserMessage(userName, finalContents);
        } else {
            // create a user message that instructs the model to ignore it's content
            return EmptyUserMessage.INSTANCE;
        }
    }

    private static UserMessage toUserMessage(String userName, List<Content> finalContents) {
        if (userName == null) {
            return UserMessage.userMessage(finalContents);
        } else {
            return UserMessage.userMessage(userName, finalContents);
        }
    }

    private static void handleSpecialContentTypes(AiServiceMethodCreateInfo createInfo, Object[] methodArgs,
            List<Content> finalContents) {
        for (int i = 0; i < methodArgs.length; i++) {
            Object methodArg = methodArgs[i];
            AiServiceMethodCreateInfo.ParameterInfo parameterInfo = createInfo.getParameterInfo().get(i);
            if (methodArg instanceof Content content) {
                handleContent(content, parameterInfo, finalContents);
            } else if (methodArg instanceof List<?> list) {
                Type javaType = TypeSignatureParser.parse(parameterInfo.typeDescriptor());
                if (javaType instanceof ParameterizedType pt) {
                    Type actualTypeArgument = pt.getActualTypeArguments()[0];
                    if (Content.class.isAssignableFrom(loadClass(actualTypeArgument))) {
                        for (Object o : list) {
                            handleContent((Content) o, parameterInfo, finalContents);
                        }
                    }
                }
            } else if (methodArg instanceof Video video) {
                finalContents.add(VideoContent.from(video));
            } else if (methodArg instanceof Audio audio) {
                finalContents.add(AudioContent.from(audio));
            } else if (methodArg instanceof PdfFile pdf) {
                finalContents.add(PdfFileContent.from(pdf));
            } else if (methodArg instanceof Image image) {
                finalContents.add(ImageContent.from(image));
            } else if (parameterInfo.annotationTypes().contains(VideoUrl.class.getName())) {
                VideoContent videoContent;
                if (methodArg instanceof String s) {
                    videoContent = VideoContent.from(s);
                } else if (methodArg instanceof URI u) {
                    videoContent = VideoContent.from(u);
                } else if (methodArg instanceof URL u) {
                    try {
                        videoContent = VideoContent.from(u.toURI());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new IllegalStateException("Unsupported parameter type '" + methodArg.getClass()
                            + "' annotated with @VideoUrl. Offending AiService is '" + createInfo.getInterfaceName() + "#"
                            + createInfo.getMethodName());
                }
                finalContents.add(videoContent);
            } else if (parameterInfo.annotationTypes().contains(AudioUrl.class.getName())) {
                AudioContent audioContent;
                if (methodArg instanceof String s) {
                    audioContent = AudioContent.from(s);
                } else if (methodArg instanceof URI u) {
                    audioContent = AudioContent.from(u);
                } else if (methodArg instanceof URL u) {
                    try {
                        audioContent = AudioContent.from(u.toURI());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new IllegalStateException("Unsupported parameter type '" + methodArg.getClass()
                            + "' annotated with @AudioUrl. Offending AiService is '" + createInfo.getInterfaceName() + "#"
                            + createInfo.getMethodName());
                }
                finalContents.add(audioContent);
            } else if (parameterInfo.annotationTypes().contains(PdfUrl.class.getName())) {
                PdfFileContent pdfFileContent;
                if (methodArg instanceof String s) {
                    pdfFileContent = PdfFileContent.from(s);
                } else if (methodArg instanceof URI u) {
                    pdfFileContent = PdfFileContent.from(u);
                } else if (methodArg instanceof URL u) {
                    try {
                        pdfFileContent = PdfFileContent.from(u.toURI());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new IllegalStateException("Unsupported parameter type '" + methodArg.getClass()
                            + "' annotated with @PdfUrl. Offending AiService is '" + createInfo.getInterfaceName() + "#"
                            + createInfo.getMethodName());
                }
                finalContents.add(pdfFileContent);
            } else if (parameterInfo.annotationTypes().contains(ImageUrl.class.getName())) {
                ImageContent imageContent;
                if (methodArg instanceof String s) {
                    imageContent = ImageContent.from(s);
                } else if (methodArg instanceof URI u) {
                    imageContent = ImageContent.from(u);
                } else if (methodArg instanceof URL u) {
                    try {
                        imageContent = ImageContent.from(u.toURI());
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    throw new IllegalStateException("Unsupported parameter type '" + methodArg.getClass()
                            + "' annotated with @ImageUrl. Offending AiService is '" + createInfo.getInterfaceName() + "#"
                            + createInfo.getMethodName());
                }
                finalContents.add(imageContent);
            }
        }
    }

    private static Class<?> loadClass(Type actualTypeArgument) {
        try {
            return Class.forName(actualTypeArgument.getTypeName(), false, Thread.currentThread()
                    .getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleContent(Content content, AiServiceMethodCreateInfo.ParameterInfo parameterInfo,
            List<Content> finalContents) {
        if (parameterInfo.annotationTypes().contains(dev.langchain4j.service.UserMessage.class.getName())) {
            finalContents.add(content);
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
                if (entry.getKey() != null) {
                    variables.put(entry.getKey(), value);
                }
            }
        }

        return variables;
    }

    private static Object transformTemplateParamValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value.getClass().isArray()) {
            // Qute does not transform these values but LangChain4j expects to be converted
            // to a [item1, item2, item3] like syntax
            return Arrays.toString((Object[]) value);
        }
        return value;
    }

    private static Object memoryId(AiServiceMethodCreateInfo createInfo, Object[] methodArgs,
            boolean hasChatMemoryProvider) {
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
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.langchain4j.ai-service.max-tool-executions", Integer.class)
                .orElse(
                        DEFAULT_MAX_SEQUENTIAL_TOOL_EXECUTIONS);
    }

    private static Executor createExecutor() {
        InstanceHandle<ManagedExecutor> executor = Arc.container().instance(ManagedExecutor.class);
        return executor.isAvailable() ? executor.get() : Infrastructure.getDefaultExecutor();
    }

    private static boolean isImmediateReturnTool(String toolName, ToolExecutor toolExecutor,
            Set<String> immediateReturnToolNames) {
        // Check if the executor itself declares immediate return behavior
        if (toolExecutor instanceof QuarkusToolExecutor quarkusExecutor) {
            if (quarkusExecutor.returnBehavior() == ReturnBehavior.IMMEDIATE) {
                return true;
            }
        }

        // Otherwise, check if the tool name is in the immediate return set
        return immediateReturnToolNames.contains(toolName);
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
}
