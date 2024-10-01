package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Exceptions.runtime;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
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
import io.quarkiverse.langchain4j.audit.Audit;
import io.quarkiverse.langchain4j.audit.AuditService;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.runtime.ContextLocals;
import io.quarkiverse.langchain4j.runtime.QuarkusServiceOutputParser;
import io.quarkiverse.langchain4j.runtime.ResponseSchemaUtil;
import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiEmitter;

/**
 * Provides the basic building blocks that the generated Interface methods call into
 */
public class AiServiceMethodImplementationSupport {

    private static final Logger log = Logger.getLogger(AiServiceMethodImplementationSupport.class);
    private static final int MAX_SEQUENTIAL_TOOL_EXECUTIONS = 10;
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

        AuditService auditService = context.auditService;
        Audit audit = null;
        if (auditService != null) {
            audit = auditService.create(new Audit.CreateInfo(createInfo.getInterfaceName(), createInfo.getMethodName(),
                    methodArgs, createInfo.getMemoryIdParamPosition()));
        }

        // TODO: add validation
        try {
            var result = doImplement(createInfo, methodArgs, context, audit);
            if (audit != null) {
                audit.onCompletion(result);
                auditService.complete(audit);
            }
            return result;
        } catch (Exception e) {
            if (audit != null) {
                audit.onFailure(e);
                auditService.complete(audit);
            }
            throw e;
        }
    }

    private static Object doImplement(AiServiceMethodCreateInfo methodCreateInfo, Object[] methodArgs,
            QuarkusAiServiceContext context, Audit audit) {
        Object memoryId = memoryId(methodCreateInfo, methodArgs, context.chatMemoryProvider != null);
        Optional<SystemMessage> systemMessage = prepareSystemMessage(methodCreateInfo, methodArgs,
                context.hasChatMemory() ? context.chatMemory(memoryId).messages() : Collections.emptyList());
        UserMessage userMessage = prepareUserMessage(context, methodCreateInfo, methodArgs);

        Type returnType = methodCreateInfo.getReturnType();
        if (isImage(returnType) || isResultImage(returnType)) {
            return doImplementGenerateImage(methodCreateInfo, context, audit, systemMessage, userMessage, memoryId, returnType);
        }

        if (audit != null) {
            audit.initialMessages(systemMessage, userMessage);
        }

        boolean needsMemorySeed = needsMemorySeed(context, memoryId); // we need to know figure this out before we add the system and user message

        boolean hasMethodSpecificTools = methodCreateInfo.getToolClassNames() != null
                && !methodCreateInfo.getToolClassNames().isEmpty();
        List<ToolSpecification> toolSpecifications = hasMethodSpecificTools ? methodCreateInfo.getToolSpecifications()
                : context.toolSpecifications;
        Map<String, ToolExecutor> toolExecutors = hasMethodSpecificTools ? methodCreateInfo.getToolExecutors()
                : context.toolExecutors;

        if (context.toolProvider != null) {
            toolSpecifications = new ArrayList<>();
            toolExecutors = new HashMap<>();
            ToolProviderRequest request = new ToolProviderRequest(memoryId, userMessage);
            ToolProviderResult result = context.toolProvider.provideTools(request);
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

            if (!isMulti(returnType)) {
                augmentationResult = context.retrievalAugmentor.augment(augmentationRequest);
                userMessage = (UserMessage) augmentationResult.chatMessage();
            } else {
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
                                GuardrailsSupport.invokeInputGuardrails(methodCreateInfo, (UserMessage) augmentedUserMessage,
                                        context.chatMemory(memoryId), ar);
                                List<ChatMessage> messagesToSend = messagesToSend(augmentedUserMessage, needsMemorySeed);
                                return Multi.createFrom()
                                        .emitter(new MultiEmitterConsumer(messagesToSend, effectiveToolSpecifications,
                                                finalToolExecutors,
                                                ar.contents(),
                                                context,
                                                memoryId));
                            }

                            private List<ChatMessage> messagesToSend(ChatMessage augmentedUserMessage,
                                    boolean needsMemorySeed) {
                                List<ChatMessage> messagesToSend;
                                ChatMemory chatMemory;
                                if (context.hasChatMemory()) {
                                    chatMemory = context.chatMemory(memoryId);
                                    messagesToSend = createMessagesToSendForExistingMemory(systemMessage, augmentedUserMessage,
                                            chatMemory, needsMemorySeed, context, methodCreateInfo);
                                } else {
                                    messagesToSend = createMessagesToSendForNoMemory(systemMessage, augmentedUserMessage,
                                            needsMemorySeed, context, methodCreateInfo);
                                }
                                return messagesToSend;
                            }
                        });
            }
        }

        GuardrailsSupport.invokeInputGuardrails(methodCreateInfo, userMessage,
                context.hasChatMemory() ? context.chatMemory(memoryId) : null,
                augmentationResult);

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

        if (isTokenStream(returnType)) {
            // TODO Indicate the output guardrails cannot be used when streaming
            chatMemory.commit(); // for streaming cases, we really have to commit because all alternatives are worse
            return new AiServiceTokenStream(messagesToSend, toolSpecifications, toolExecutors,
                    (augmentationResult != null ? augmentationResult.contents() : null), context, memoryId);
        }

        if (isMulti(returnType)) {
            // TODO Indicate the output guardrails cannot be used when streaming
            chatMemory.commit(); // for streaming cases, we really have to commit because all alternatives are worse
            return Multi.createFrom().emitter(new MultiEmitterConsumer(messagesToSend, toolSpecifications,
                    toolExecutors, augmentationResult != null ? augmentationResult.contents() : null, context, memoryId));
        }

        Future<Moderation> moderationFuture = triggerModerationIfNeeded(context, methodCreateInfo, messagesToSend);

        log.debug("Attempting to obtain AI response");

        Response<AiMessage> response = toolSpecifications == null
                ? context.chatModel.generate(messagesToSend)
                : context.chatModel.generate(messagesToSend, toolSpecifications);
        log.debug("AI response obtained");
        if (audit != null) {
            audit.addLLMToApplicationMessage(response);
        }
        TokenUsage tokenUsageAccumulator = response.tokenUsage();

        verifyModerationIfNeeded(moderationFuture);

        int executionsLeft = MAX_SEQUENTIAL_TOOL_EXECUTIONS;
        while (true) {

            if (executionsLeft-- == 0) {
                throw runtime("Something is wrong, exceeded %s sequential tool executions",
                        MAX_SEQUENTIAL_TOOL_EXECUTIONS);
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
                if (audit != null) {
                    audit.addApplicationToLLMMessage(toolExecutionResultMessage);
                }
                chatMemory.add(toolExecutionResultMessage);
            }

            log.debug("Attempting to obtain AI response");
            response = context.chatModel.generate(chatMemory.messages(), toolSpecifications);
            log.debug("AI response obtained");

            if (audit != null) {
                audit.addLLMToApplicationMessage(response);
            }

            tokenUsageAccumulator = tokenUsageAccumulator.add(response.tokenUsage());
        }

        response = GuardrailsSupport.invokeOutputGuardrails(methodCreateInfo, chatMemory, context.chatModel, response,
                toolSpecifications,
                new OutputGuardrail.OutputGuardrailParams(response.content(), chatMemory, augmentationResult));

        // everything worked as expected so let's commit the messages
        chatMemory.commit();

        response = Response.from(response.content(), tokenUsageAccumulator, response.finishReason());
        if (isResult(returnType)) {
            var parsedResponse = SERVICE_OUTPUT_PARSER.parse(response, resultTypeParam((ParameterizedType) returnType));
            return Result.builder()
                    .content(parsedResponse)
                    .tokenUsage(tokenUsageAccumulator)
                    .sources(augmentationResult == null ? null : augmentationResult.contents())
                    .finishReason(response.finishReason())
                    .build();
        } else {
            return SERVICE_OUTPUT_PARSER.parse(response, returnType);
        }
    }

    private static Object doImplementGenerateImage(AiServiceMethodCreateInfo methodCreateInfo, QuarkusAiServiceContext context,
            Audit audit, Optional<SystemMessage> systemMessage, UserMessage userMessage,
            Object memoryId, Type returnType) {
        String imagePrompt;
        if (systemMessage.isPresent()) {
            imagePrompt = systemMessage.get().text() + "\n" + userMessage.singleText();
        } else {
            imagePrompt = userMessage.singleText();
        }
        if (audit != null) {
            // TODO: we can't support addLLMToApplicationMessage for now as it is tied to AiMessage
            audit.initialMessages(systemMessage, userMessage);
        }

        //TODO: does it make sense to use the retrievalAugmentor here? What good would be for us telling the LLM to use this or that information to create an image?
        AugmentationResult augmentationResult = null;

        // TODO: we can only support input guardrails for now as it is tied to AiMessage
        GuardrailsSupport.invokeInputGuardrails(methodCreateInfo, userMessage,
                context.hasChatMemory() ? context.chatMemory(memoryId) : null,
                augmentationResult);

        Response<Image> imageResponse = context.imageModel.generate(imagePrompt);
        if (audit != null) {
            audit.onCompletion(imageResponse.content());
        }

        if (isImage(returnType)) {
            return imageResponse.content();
        } else if (isResultImage(returnType)) {
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

    private static boolean isTokenStream(Type returnType) {
        return isTypeOf(returnType, TokenStream.class);
    }

    private static boolean isMulti(Type returnType) {
        return isTypeOf(returnType, Multi.class);
    }

    private static boolean isResult(Type returnType) {
        return isTypeOf(returnType, Result.class);
    }

    private static Type resultTypeParam(ParameterizedType returnType) {
        if (!isTypeOf(returnType, Result.class)) {
            throw new IllegalStateException("Can only be called with Result<T> type");
        }
        return returnType.getActualTypeArguments()[0];
    }

    private static boolean isImage(Type returnType) {
        return isTypeOf(returnType, Image.class);
    }

    private static boolean isResultImage(Type returnType) {
        if (!isImage(returnType)) {
            return false;
        }
        return isImage(resultTypeParam((ParameterizedType) returnType));
    }

    private static boolean isTypeOf(Type type, Class<?> clazz) {
        if (type instanceof Class<?>) {
            return type.equals(clazz);
        }
        if (type instanceof ParameterizedType pt) {
            return isTypeOf(pt.getRawType(), clazz);
        }
        throw new IllegalStateException("Unsupported return type " + type);
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
            Object[] methodArgs) {
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
            Map<String, Object> templateParams = new HashMap<>();
            Map<String, Integer> nameToParamPosition = templateInfo.nameToParamPosition();
            for (var entry : nameToParamPosition.entrySet()) {
                Object value = transformTemplateParamValue(methodArgs[entry.getValue()]);
                templateParams.put(entry.getKey(), value);
            }
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

            // No response schema placeholder found in the @SystemMessage and @UserMessage, concat it to the UserMessage.
            if (!createInfo.getResponseSchemaInfo().isInSystemMessage() && !hasResponseSchema) {
                templateText = templateText.concat(ResponseSchemaUtil.placeholder());
            }

            // we do not need to apply the instructions as they have already been added to the template text at build time
            templateParams.put(ResponseSchemaUtil.templateParam(),
                    createInfo.getResponseSchemaInfo().outputFormatInstructions());
            Prompt prompt = PromptTemplate.from(templateText).apply(templateParams);
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

            // TODO: Understand how to enable the {response_schema} for the @StructuredPrompt.
            String text = toString(argValue);
            return createUserMessage(userName, imageContent,
                    text.concat(createInfo.getResponseSchemaInfo().outputFormatInstructions()));
        } else {
            throw new IllegalStateException("Unable to construct UserMessage for class '" + context.aiServiceClass.getName()
                    + "'. Please contact the maintainers");
        }
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

    private static class MultiEmitterConsumer implements Consumer<MultiEmitter<? super String>> {
        private final List<ChatMessage> messagesToSend;
        private final List<ToolSpecification> toolSpecifications;
        private final Map<String, ToolExecutor> toolExecutors;
        private final List<dev.langchain4j.rag.content.Content> contents;
        private final QuarkusAiServiceContext context;
        private final Object memoryId;

        public MultiEmitterConsumer(List<ChatMessage> messagesToSend,
                List<ToolSpecification> toolSpecifications,
                Map<String, ToolExecutor> toolExecutors,
                List<dev.langchain4j.rag.content.Content> contents,
                QuarkusAiServiceContext context,
                Object memoryId) {
            this.messagesToSend = messagesToSend;
            this.toolSpecifications = toolSpecifications;
            this.toolExecutors = toolExecutors;
            this.contents = contents;
            this.context = context;
            this.memoryId = memoryId;
        }

        @Override
        public void accept(MultiEmitter<? super String> em) {
            new AiServiceTokenStream(messagesToSend, toolSpecifications, toolExecutors, contents, context, memoryId)
                    .onNext(em::emit)
                    .onComplete(new Consumer<>() {
                        @Override
                        public void accept(Response<AiMessage> message) {
                            em.complete();
                        }
                    })
                    .onError(em::fail)
                    .start();
        }
    }

    private record GuardRailsResult(boolean success, Class<? extends OutputGuardrail> bean, Exception failure,
            boolean retry, String reprompt) {

        static GuardRailsResult SUCCESS = new GuardRailsResult(true, null, null, false, null);

    }
}
