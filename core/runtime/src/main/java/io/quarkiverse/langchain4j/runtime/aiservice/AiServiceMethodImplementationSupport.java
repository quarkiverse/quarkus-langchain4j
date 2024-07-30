package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.service.AiServices.removeToolMessages;
import static dev.langchain4j.service.AiServices.verifyModerationIfNeeded;
import static io.quarkiverse.langchain4j.runtime.ResponseSchemaUtil.hasResponseSchema;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
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
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.spi.ServiceHelper;
import io.quarkiverse.langchain4j.audit.Audit;
import io.quarkiverse.langchain4j.audit.AuditService;
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
            log.errorv(e, "Execution of {0}#{1} failed", createInfo.getInterfaceName(), createInfo.getMethodName());
            if (audit != null) {
                audit.onFailure(e);
                auditService.complete(audit);
            }
            throw e;
        }
    }

    private static Object doImplement(AiServiceMethodCreateInfo methodCreateInfo, Object[] methodArgs,
            QuarkusAiServiceContext context, Audit audit) {
        Optional<SystemMessage> systemMessage = prepareSystemMessage(methodCreateInfo, methodArgs);
        UserMessage userMessage = prepareUserMessage(context, methodCreateInfo, methodArgs);

        if (audit != null) {
            audit.initialMessages(systemMessage, userMessage);
        }

        Object memoryId = memoryId(methodCreateInfo, methodArgs, context.chatMemoryProvider != null);
        Type returnType = methodCreateInfo.getReturnType();
        AugmentationResult augmentationResult;

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
                                List<ChatMessage> messagesToSend = messagesToSend(augmentedUserMessage);

                                return Multi.createFrom().emitter(new MultiEmitterConsumer(messagesToSend, context, memoryId));
                            }

                            private List<ChatMessage> messagesToSend(ChatMessage augmentedUserMessage) {
                                List<ChatMessage> messagesToSend;
                                ChatMemory chatMemory;
                                if (context.hasChatMemory()) {
                                    chatMemory = context.chatMemory(memoryId);
                                    if (systemMessage.isPresent()) {
                                        chatMemory.add(systemMessage.get());
                                    }
                                    chatMemory.add(augmentedUserMessage);
                                    messagesToSend = chatMemory.messages();
                                } else {
                                    messagesToSend = new ArrayList<>();
                                    if (systemMessage.isPresent()) {
                                        messagesToSend.add(systemMessage.get());
                                    }
                                    messagesToSend.add(augmentedUserMessage);
                                }
                                return messagesToSend;
                            }
                        });
            }
        }

        CommittableChatMemory chatMemory;
        List<ChatMessage> messagesToSend;

        if (context.hasChatMemory()) {
            // we want to defer saving the new messages because the service could fail and be retried
            chatMemory = new DefaultCommittableChatMemory(context.chatMemory(memoryId));
            if (systemMessage.isPresent()) {
                chatMemory.add(systemMessage.get());
            }
            chatMemory.add(userMessage);

            messagesToSend = chatMemory.messages();
        } else {
            chatMemory = new NoopChatMemory();

            messagesToSend = new ArrayList<>();
            if (systemMessage.isPresent()) {
                messagesToSend.add(systemMessage.get());
            }
            messagesToSend.add(userMessage);
        }

        if (isTokenStream(returnType)) {
            chatMemory.commit(); // for streaming cases, we really have to commit because all alternatives are worse
            return new AiServiceTokenStream(messagesToSend, context, memoryId);
        }

        if (isMulti(returnType)) {
            chatMemory.commit(); // for streaming cases, we really have to commit because all alternatives are worse
            return Multi.createFrom().emitter(new MultiEmitterConsumer(messagesToSend, context, memoryId));
        }

        Future<Moderation> moderationFuture = triggerModerationIfNeeded(context, methodCreateInfo, messagesToSend);

        log.debug("Attempting to obtain AI response");

        List<ToolSpecification> toolSpecifications = context.toolSpecifications;
        Map<String, ToolExecutor> toolExecutors = context.toolExecutors;
        // override with method specific info
        if (methodCreateInfo.getToolClassNames() != null && !methodCreateInfo.getToolClassNames().isEmpty()) {
            toolSpecifications = methodCreateInfo.getToolSpecifications();
            toolExecutors = methodCreateInfo.getToolExecutors();
        }

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

        // everything worked as expected so let's commit the messages
        chatMemory.commit();

        response = Response.from(response.content(), tokenUsageAccumulator, response.finishReason());
        return SERVICE_OUTPUT_PARSER.parse(response, returnType);
    }

    private static boolean isTokenStream(Type returnType) {
        return isTypeOf(returnType, TokenStream.class);
    }

    private static boolean isMulti(Type returnType) {
        return isTypeOf(returnType, Multi.class);
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

    private static Optional<SystemMessage> prepareSystemMessage(AiServiceMethodCreateInfo createInfo, Object[] methodArgs) {
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
        Prompt prompt = PromptTemplate.from(systemMessageInfo.text().get()).apply(templateParams);
        return Optional.of(prompt.toSystemMessage());
    }

    private static UserMessage prepareUserMessage(AiServiceContext context, AiServiceMethodCreateInfo createInfo,
            Object[] methodArgs) {
        AiServiceMethodCreateInfo.UserMessageInfo userMessageInfo = createInfo.getUserMessageInfo();

        String userName = null;
        if (userMessageInfo.userNameParamPosition().isPresent()) {
            userName = methodArgs[userMessageInfo.userNameParamPosition().get()]
                    .toString(); // LangChain4j does this, but might want to make anything other than a String a build time error
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
            return createUserMessage(userName, prompt.text());

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
            return createUserMessage(userName, text.concat(createInfo.getResponseSchemaInfo().outputFormatInstructions()));
        } else {
            throw new IllegalStateException("Unable to construct UserMessage for class '" + context.aiServiceClass.getName()
                    + "'. Please contact the maintainers");
        }
    }

    private static UserMessage createUserMessage(String name, String text) {
        if (name == null) {
            return userMessage(text);
        } else {
            return userMessage(name, text);
        }
    }

    private static Object transformTemplateParamValue(Object value) {
        if (value.getClass().isArray()) {
            // Qute does not transform these values but LangChain4j expects to be converted to a [item1, item2, item3] like systax
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
        private final QuarkusAiServiceContext context;
        private final Object memoryId;

        public MultiEmitterConsumer(List<ChatMessage> messagesToSend, QuarkusAiServiceContext context,
                Object memoryId) {
            this.messagesToSend = messagesToSend;
            this.context = context;
            this.memoryId = memoryId;
        }

        @Override
        public void accept(MultiEmitter<? super String> em) {
            new AiServiceTokenStream(messagesToSend, context, memoryId)
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
}
