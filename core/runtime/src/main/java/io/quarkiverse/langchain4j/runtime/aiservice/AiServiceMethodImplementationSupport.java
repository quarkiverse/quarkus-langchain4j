package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Exceptions.runtime;
import static dev.langchain4j.service.AiServices.removeToolMessages;
import static dev.langchain4j.service.AiServices.verifyModerationIfNeeded;
import static dev.langchain4j.service.ServiceOutputParser.parse;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
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
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.service.AiServiceContext;
import dev.langchain4j.service.AiServiceTokenStream;
import dev.langchain4j.service.TokenStream;
import io.quarkiverse.langchain4j.audit.Audit;
import io.quarkiverse.langchain4j.audit.AuditService;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.ManagedContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiEmitter;

/**
 * Provides the basic building blocks that the generated Interface methods call into
 */
public class AiServiceMethodImplementationSupport {

    private static final Logger log = Logger.getLogger(AiServiceMethodImplementationSupport.class);

    private static final int MAX_SEQUENTIAL_TOOL_EXECUTIONS = 10;

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

    private static Object doImplement(AiServiceMethodCreateInfo createInfo, Object[] methodArgs,
            QuarkusAiServiceContext context, Audit audit) {
        Optional<SystemMessage> systemMessage = prepareSystemMessage(createInfo, methodArgs);
        UserMessage userMessage = prepareUserMessage(context, createInfo, methodArgs);

        if (audit != null) {
            audit.initialMessages(systemMessage, userMessage);
        }

        Object memoryId = memoryId(createInfo, methodArgs, context.chatMemoryProvider != null);

        if (context.retrievalAugmentor != null) { // TODO extract method/class
            List<ChatMessage> chatMemory = context.hasChatMemory()
                    ? context.chatMemory(memoryId).messages()
                    : null;
            Metadata metadata = Metadata.from(userMessage, memoryId, chatMemory);
            userMessage = context.retrievalAugmentor.augment(userMessage, metadata);
        }

        // TODO give user ability to provide custom OutputParser
        String outputFormatInstructions = createInfo.getUserMessageInfo().getOutputFormatInstructions();
        userMessage = UserMessage.from(userMessage.text() + outputFormatInstructions);

        if (context.hasChatMemory()) {
            ChatMemory chatMemory = context.chatMemory(memoryId);
            if (systemMessage.isPresent()) {
                chatMemory.add(systemMessage.get());
            }
            chatMemory.add(userMessage);
        }

        List<ChatMessage> messages;
        if (context.hasChatMemory()) {
            messages = context.chatMemory(memoryId).messages();
        } else {
            messages = new ArrayList<>();
            systemMessage.ifPresent(messages::add);
            messages.add(userMessage);
        }

        Class<?> returnType = createInfo.getReturnType();
        if (returnType.equals(TokenStream.class)) {
            return new AiServiceTokenStream(messages, context, memoryId);
        }

        if (returnType.equals(Multi.class)) {
            return Multi.createFrom().emitter(new Consumer<MultiEmitter<? super String>>() {
                @Override
                public void accept(MultiEmitter<? super String> em) {
                    new AiServiceTokenStream(messages, context, memoryId)
                            .onNext(em::emit)
                            .onComplete(new Consumer<Response<AiMessage>>() {
                                @Override
                                public void accept(Response<AiMessage> message) {
                                    em.complete();
                                }
                            })
                            .onError(em::fail)
                            .start();
                }
            });
        }

        Future<Moderation> moderationFuture = triggerModerationIfNeeded(context, createInfo, messages);

        log.debug("Attempting to obtain AI response");
        Response<AiMessage> response = context.toolSpecifications == null
                ? context.chatModel.generate(messages)
                : context.chatModel.generate(messages, context.toolSpecifications);
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

            if (context.hasChatMemory()) {
                context.chatMemory(memoryId).add(response.content());
            }

            if (!aiMessage.hasToolExecutionRequests()) {
                break;
            }

            ChatMemory chatMemory = context.chatMemory(memoryId);

            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                log.debugv("Attempting to execute tool {0}", toolExecutionRequest);
                ToolExecutor toolExecutor = context.toolExecutors.get(toolExecutionRequest.name());
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
            response = context.chatModel.generate(chatMemory.messages(), context.toolSpecifications);
            log.debug("AI response obtained");

            if (audit != null) {
                audit.addLLMToApplicationMessage(response);
            }

            tokenUsageAccumulator = tokenUsageAccumulator.add(response.tokenUsage());
        }

        response = Response.from(response.content(), tokenUsageAccumulator, response.finishReason());
        return parse(response, returnType);
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
        Map<String, Integer> nameToParamPosition = systemMessageInfo.getNameToParamPosition();
        for (var entry : nameToParamPosition.entrySet()) {
            templateParams.put(entry.getKey(), methodArgs[entry.getValue()]);
        }
        Prompt prompt = PromptTemplate.from(systemMessageInfo.getText().get()).apply(templateParams);
        return Optional.of(prompt.toSystemMessage());
    }

    private static UserMessage prepareUserMessage(AiServiceContext context, AiServiceMethodCreateInfo createInfo,
            Object[] methodArgs) {
        AiServiceMethodCreateInfo.UserMessageInfo userMessageInfo = createInfo.getUserMessageInfo();

        String userName = null;
        if (userMessageInfo.getUserNameParamPosition().isPresent()) {
            userName = methodArgs[userMessageInfo.getUserNameParamPosition().get()]
                    .toString(); // LangChain4j does this, but might want to make anything other than a String a build time error
        }

        if (userMessageInfo.getTemplate().isPresent()) {
            AiServiceMethodCreateInfo.TemplateInfo templateInfo = userMessageInfo.getTemplate().get();
            Map<String, Object> templateParams = new HashMap<>();
            Map<String, Integer> nameToParamPosition = templateInfo.getNameToParamPosition();
            for (var entry : nameToParamPosition.entrySet()) {
                Object value = transformTemplateParamValue(methodArgs[entry.getValue()]);
                templateParams.put(entry.getKey(), value);
            }
            String templateText;
            if (templateInfo.getText().isPresent()) {
                templateText = templateInfo.getText().get();
            } else {
                templateText = (String) methodArgs[templateInfo.getMethodParamPosition().get()];
            }
            // we do not need to apply the instructions as they have already been added to the template text at build time
            Prompt prompt = PromptTemplate.from(templateText).apply(templateParams);

            return createUserMessage(userName, prompt.text());
        } else if (userMessageInfo.getParamPosition().isPresent()) {
            Integer paramIndex = userMessageInfo.getParamPosition().get();
            Object argValue = methodArgs[paramIndex];
            if (argValue == null) {
                throw new IllegalArgumentException(
                        "Unable to construct UserMessage for class '" + context.aiServiceClass.getName()
                                + "' because parameter with index "
                                + paramIndex + " is null");
            }
            return createUserMessage(userName, toString(argValue));
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
            // first we try to use the current context in order to make sure that we don't interleave chat messages of concurrent requests
            ArcContainer container = Arc.container();
            if (container != null) {
                ManagedContext requestContext = container.requestContext();
                if (requestContext.isActive()) {
                    return requestContext.getState();
                }
            }
        }
        // fallback to the default since there is nothing else we can really use here
        return "default";
    }

    //TODO: share these methods with LangChain4j

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
}
