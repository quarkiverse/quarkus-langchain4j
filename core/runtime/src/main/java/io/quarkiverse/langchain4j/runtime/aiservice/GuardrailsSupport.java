package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static io.quarkiverse.langchain4j.guardrails.InputGuardrailParams.rewriteUserMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.service.guardrail.GuardrailService;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.InputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.audit.OutputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.audit.internal.DefaultInputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.audit.internal.DefaultOutputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.guardrails.Guardrail;
import io.quarkiverse.langchain4j.guardrails.GuardrailParams;
import io.quarkiverse.langchain4j.guardrails.GuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.NoopChatExecutor;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent.AccumulatedResponseEvent;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatEvent.ChatEventType;
import io.smallrye.mutiny.Multi;

public class GuardrailsSupport {
    static UserMessage executeInputGuardrails(GuardrailService guardrailService, UserMessage userMessage,
            AiServiceMethodCreateInfo methodCreateInfo, ChatMemory chatMemory, AugmentationResult augmentationResult,
            Map<String, Object> templateVariables) {
        var um = userMessage;

        if (guardrailService.hasInputGuardrails(methodCreateInfo)) {
            var request = InputGuardrailRequest.builder()
                    .userMessage(userMessage)
                    .commonParams(
                            GuardrailRequestParams.builder()
                                    .chatMemory(chatMemory)
                                    .augmentationResult(augmentationResult)
                                    .userMessageTemplate(methodCreateInfo.getUserMessageTemplate())
                                    .variables(templateVariables)
                                    .build())
                    .build();

            um = guardrailService.executeGuardrails(methodCreateInfo, request);
        }

        return um;
    }

    static <T> T executeOutputGuardrails(GuardrailService guardrailService, AiServiceMethodCreateInfo methodCreateInfo,
            ChatResponse response, ChatExecutor chatExecutor, CommittableChatMemory committableChatMemory,
            AugmentationResult augmentationResult, Map<String, Object> templateVariables,
            @Deprecated(forRemoval = true) T quarkusSpecificGuardrailResult) {

        /**
         * The quarkusSpecificGuardrailResult will be removed when the quarkus-specific guardrails implementation is removed
         */
        T result = quarkusSpecificGuardrailResult;

        if (guardrailService.hasOutputGuardrails(methodCreateInfo)) {
            var request = OutputGuardrailRequest.builder()
                    .responseFromLLM(response)
                    .chatExecutor(chatExecutor)
                    .requestParams(
                            GuardrailRequestParams.builder()
                                    .chatMemory(committableChatMemory)
                                    .augmentationResult(augmentationResult)
                                    .userMessageTemplate(methodCreateInfo.getUserMessageTemplate())
                                    .variables(templateVariables)
                                    .build())
                    .build();

            result = guardrailService.executeGuardrails(methodCreateInfo, request);
        }

        return result;
    }

    static boolean isOutputGuardrailRetry(Throwable t) {
        return (t instanceof OutputGuardrailException) &&
                t.getMessage().toLowerCase().contains("the guardrails have reached the maximum number of retries.");
    }

    static class OutputGuardrailStreamingMapper
            implements Function<Object, Object> {
        private final GuardrailService guardrailService;
        private final AiServiceMethodCreateInfo methodCreateInfo;
        private final CommittableChatMemory committableChatMemory;
        private final AugmentationResult augmentationResult;
        private final Map<String, Object> templateVariables;
        private final boolean isStringMulti;

        OutputGuardrailStreamingMapper(GuardrailService guardrailService, AiServiceMethodCreateInfo methodCreateInfo,
                CommittableChatMemory committableChatMemory, AugmentationResult augmentationResult,
                Map<String, Object> templateVariables, boolean isStringMulti) {
            this.guardrailService = guardrailService;
            this.methodCreateInfo = methodCreateInfo;
            this.committableChatMemory = committableChatMemory;
            this.augmentationResult = augmentationResult;
            this.templateVariables = templateVariables;
            this.isStringMulti = isStringMulti;
        }

        private Object apply(ChatEvent chunk) {
            if (chunk.getEventType() == ChatEventType.AccumulatedResponse) {
                var accumulatedChunk = (ChatEvent.AccumulatedResponseEvent) chunk;
                var metadata = accumulatedChunk.getMetadata();
                var guardrailResult = executeOutputGuardrails(
                        guardrailService,
                        methodCreateInfo,
                        ChatResponse.builder()
                                .aiMessage(AiMessage.from(accumulatedChunk.getMessage()))
                                .build(),
                        new NoopChatExecutor(),
                        committableChatMemory,
                        augmentationResult,
                        templateVariables,
                        null);

                if (guardrailResult instanceof ChatResponse) {
                    String message = ((ChatResponse) guardrailResult).aiMessage().text();
                    return isStringMulti ? message : new AccumulatedResponseEvent(message, metadata);
                } else if (guardrailResult instanceof String) {
                    return isStringMulti ? (String) guardrailResult
                            : new AccumulatedResponseEvent((String) guardrailResult, metadata);
                } else if (guardrailResult != null) {
                    return isStringMulti ? guardrailResult.toString()
                            : new AccumulatedResponseEvent(guardrailResult.toString(), metadata);
                }
            }

            return chunk;
        }

        private Object apply(String chunk) {
            var guardrailResult = executeOutputGuardrails(
                    guardrailService,
                    methodCreateInfo,
                    ChatResponse.builder()
                            .aiMessage(AiMessage.from(chunk))
                            .build(),
                    new NoopChatExecutor(),
                    committableChatMemory,
                    augmentationResult,
                    templateVariables,
                    null);

            if (guardrailResult instanceof ChatResponse) {
                return ((ChatResponse) guardrailResult).aiMessage().text();
            } else if (guardrailResult instanceof String) {
                return (String) guardrailResult;
            } else if (guardrailResult != null) {
                // TODO is this really needed
                return guardrailResult.toString();
            }

            return chunk;
        }

        @Override
        public Object apply(Object chunk) {
            if (chunk instanceof ChatEvent) {
                return apply((ChatEvent) chunk);
            } else if (chunk instanceof String) {
                return apply((String) chunk);
            }

            return chunk;
        }
    }

    /**
     * @deprecated Deprecated in favor of upstream implementation
     */
    @Deprecated(forRemoval = true)
    public static UserMessage invokeInputGuardRails(AiServiceMethodCreateInfo methodCreateInfo, UserMessage userMessage,
            ChatMemory chatMemory, AugmentationResult augmentationResult, Map<String, Object> templateVariables,
            BeanManager beanManager, AuditSourceInfo auditSourceInfo) {
        InputGuardrailResult result;
        try {

            String userMessageTemplate = methodCreateInfo.getUserMessageTemplate();

            result = invokeInputGuardRails(methodCreateInfo,
                    new InputGuardrailParams(userMessage, chatMemory, augmentationResult, userMessageTemplate,
                            Collections.unmodifiableMap(templateVariables)),
                    beanManager, auditSourceInfo);
        } catch (Exception e) {
            throw new GuardrailException(e.getMessage(), e);
        }
        if (!result.isSuccess()) {
            throw new GuardrailException(result.toString(), result.getFirstFailureException());
        }

        if (result.hasRewrittenResult()) {
            userMessage = rewriteUserMessage(userMessage, result.successfulText());
        }
        return userMessage;
    }

    /**
     * @deprecated Deprecated in favor of upstream implementation
     */
    @Deprecated(forRemoval = true)
    public static OutputGuardrailResponse invokeOutputGuardRails(AiServiceMethodCreateInfo methodCreateInfo,
            ChatMemory chatMemory,
            ChatModel chatModel,
            ChatResponse response,
            List<ToolSpecification> toolSpecifications,
            OutputGuardrailParams output, BeanManager beanManager, AuditSourceInfo auditSourceInfo) {
        int attempt = 0;
        int max = methodCreateInfo.getQuarkusGuardrailsMaxRetry();
        if (max <= 0) {
            max = 1;
        }

        OutputGuardrailResult result = null;
        while (attempt < max) {
            try {
                result = invokeOutputGuardRails(methodCreateInfo, output, beanManager, auditSourceInfo);
            } catch (Exception e) {
                throw new GuardrailException(e.getMessage(), e);
            }

            if (result.isSuccess()) {
                break;
            }

            if (result.isRetry()) {
                // Retry
                if (result.getReprompt() != null) {
                    // Retry with reprompting
                    chatMemory.add(userMessage(result.getReprompt()));
                }

                response = AiServiceMethodImplementationSupport.executeRequest(methodCreateInfo, chatMemory.messages(),
                        chatModel, toolSpecifications);
                chatMemory.add(response.aiMessage());
            } else {
                throw new GuardrailException(result.toString(), result.getFirstFailureException());
            }

            attempt++;
            output = new OutputGuardrailParams(response.aiMessage(), output.memory(),
                    output.augmentationResult(), output.userMessageTemplate(), output.variables());
        }

        if (attempt == max) {
            var failureMessages = Optional.ofNullable(result.failures())
                    .orElseGet(List::of)
                    .stream()
                    .map(OutputGuardrailResult.Failure::message)
                    .collect(Collectors.joining(System.lineSeparator()));

            throw new GuardrailException(
                    "Output validation failed. The guardrails have reached the maximum number of retries. Guardrail messages:"
                            + System.lineSeparator() + failureMessages);
        }

        return new OutputGuardrailResponse(response, result);
    }

    /**
     * @deprecated Deprecated in favor of upstream implementation
     */
    @Deprecated(forRemoval = true)
    public record OutputGuardrailResponse(ChatResponse response, OutputGuardrailResult result) {

        public boolean hasRewrittenResult() {
            return result != null && result.hasRewrittenResult();
        }

        public Object getRewrittenResult() {
            return hasRewrittenResult() ? result.successfulResult() : null;
        }
    }

    /**
     * @deprecated Deprecated in favor of upstream implementation
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("unchecked")
    static OutputGuardrailResult invokeOutputGuardRails(AiServiceMethodCreateInfo methodCreateInfo,
            OutputGuardrailParams params, BeanManager beanManager, AuditSourceInfo auditSourceInfo) {
        if (methodCreateInfo.getQuarkusOutputGuardrailsClassNames().isEmpty()) {
            return OutputGuardrailResult.success();
        }
        List<Class<? extends OutputGuardrail>> classes;
        synchronized (AiServiceMethodImplementationSupport.class) {
            classes = methodCreateInfo.getQuarkusOutputGuardrailsClasses();
            if (classes.isEmpty()) {
                for (String className : methodCreateInfo.getQuarkusOutputGuardrailsClassNames()) {
                    try {
                        classes.add((Class<? extends OutputGuardrail>) Class.forName(className, true,
                                Thread.currentThread().getContextClassLoader()));
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Could not find " + OutputGuardrail.class.getSimpleName() + " implementation class: "
                                        + className,
                                e);
                    }
                }
            }
        }

        return guardrailResult(params, (List) classes, OutputGuardrailResult.success(), OutputGuardrailResult::failure,
                beanManager, auditSourceInfo);
    }

    /**
     * @deprecated Deprecated in favor of upstream implementation
     */
    @Deprecated(forRemoval = true)
    @SuppressWarnings("unchecked")
    private static InputGuardrailResult invokeInputGuardRails(AiServiceMethodCreateInfo methodCreateInfo,
            InputGuardrailParams params, BeanManager beanManager, AuditSourceInfo auditSourceInfo) {
        if (methodCreateInfo.getQuarkusInputGuardrailsClassNames().isEmpty()) {
            return InputGuardrailResult.success();
        }
        List<Class<? extends InputGuardrail>> classes;
        synchronized (AiServiceMethodImplementationSupport.class) {
            classes = methodCreateInfo.getQuarkusInputGuardrailsClasses();
            if (classes.isEmpty()) {
                for (String className : methodCreateInfo.getQuarkusInputGuardrailsClassNames()) {
                    try {
                        classes.add((Class<? extends InputGuardrail>) Class.forName(className, true,
                                Thread.currentThread().getContextClassLoader()));
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Could not find " + InputGuardrail.class.getSimpleName() + " implementation class: "
                                        + className,
                                e);
                    }
                }
            }
        }

        return guardrailResult(params, (List) classes, InputGuardrailResult.success(), InputGuardrailResult::failure,
                beanManager, auditSourceInfo);
    }

    /**
     * @deprecated Deprecated in favor of upstream implementation
     */
    @Deprecated(forRemoval = true)
    private static <GR extends GuardrailResult> GR guardrailResult(GuardrailParams params,
            List<Class<? extends Guardrail>> classes, GR accumulatedResults,
            Function<List<? extends GuardrailResult.Failure>, GR> producer, BeanManager beanManager,
            AuditSourceInfo auditSourceInfo) {
        for (Class<? extends Guardrail> bean : classes) {
            var guardrail = CDI.current().select(bean).get();
            GR result = (GR) guardrail.validate(params).validatedBy(bean);

            if (guardrail instanceof InputGuardrail) {
                beanManager.getEvent().select(InputGuardrailExecutedEvent.class)
                        .fire(new DefaultInputGuardrailExecutedEvent(auditSourceInfo, (InputGuardrailParams) params,
                                (InputGuardrailResult) result, (Class<InputGuardrail>) guardrail.getClass()));
            } else if (guardrail instanceof OutputGuardrail) {
                beanManager.getEvent().select(OutputGuardrailExecutedEvent.class)
                        .fire(new DefaultOutputGuardrailExecutedEvent(auditSourceInfo, (OutputGuardrailParams) params,
                                (OutputGuardrailResult) result, (Class<OutputGuardrail>) guardrail.getClass()));
            }

            if (result.isFatal()) {
                return accumulatedResults.hasRewrittenResult() ? (GR) result.blockRetry() : result;
            }
            if (result.hasRewrittenResult()) {
                params = params.withText(result.successfulText());
            }
            accumulatedResults = compose(accumulatedResults, result, producer);
        }

        return accumulatedResults;
    }

    /**
     * @deprecated Deprecated in favor of upstream implementation
     */
    @Deprecated(forRemoval = true)
    private static <GR extends GuardrailResult> GR compose(GR oldResult, GR newResult,
            Function<List<? extends GuardrailResult.Failure>, GR> producer) {
        if (oldResult.isSuccess()) {
            return newResult;
        }
        if (newResult.isSuccess()) {
            return oldResult;
        }
        List<? extends GuardrailResult.Failure> failures = new ArrayList<>();
        failures.addAll(oldResult.failures());
        failures.addAll(newResult.failures());
        return producer.apply(failures);
    }

    private static class ChatResponseAccumulator {
        private final StringBuilder stringBuilder;
        private ChatResponseMetadata metadata;

        ChatResponseAccumulator() {
            this.stringBuilder = new StringBuilder();
            this.metadata = null;
        }

    }

    public static Multi<ChatEvent.AccumulatedResponseEvent> accumulate(Multi<ChatEvent> upstream,
            AiServiceMethodCreateInfo methodCreateInfo) {
        OutputTokenAccumulator accumulator;
        synchronized (AiServiceMethodImplementationSupport.class) {
            accumulator = methodCreateInfo.getOutputTokenAccumulator();
            if (accumulator == null) {
                String cn = methodCreateInfo.getOutputTokenAccumulatorClassName();
                if (cn == null) {
                    return upstream.collect()
                            .in(ChatResponseAccumulator::new, (chatResponseAccumulator, chatEvent) -> {
                                if (chatEvent
                                        .getEventType() == ChatEventType.PartialResponse) {
                                    chatResponseAccumulator.stringBuilder.append(
                                            ((ChatEvent.PartialResponseEvent) chatEvent)
                                                    .getChunk());
                                }
                                if (chatEvent
                                        .getEventType() == ChatEventType.Completed) {
                                    chatResponseAccumulator.metadata = ((ChatEvent.ChatCompletedEvent) chatEvent)
                                            .getChatResponse().metadata();
                                }
                            })
                            .map(acc -> new AccumulatedResponseEvent(
                                    acc.stringBuilder.toString(), acc.metadata))
                            .toMulti();
                }
                try {
                    Class<? extends OutputTokenAccumulator> clazz = Class
                            .forName(cn, true, Thread.currentThread().getContextClassLoader())
                            .asSubclass(OutputTokenAccumulator.class);
                    accumulator = CDI.current().select(clazz).get();
                    methodCreateInfo.setOutputTokenAccumulator(accumulator);
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Could not find " + OutputTokenAccumulator.class.getSimpleName() + " implementation class: " + cn,
                            e);
                }
            }
        }
        var actual = accumulator;
        AtomicReference<ChatResponseMetadata> metadataAtomicReference = new AtomicReference<>();
        return upstream.invoke(it -> {
            if (it.getEventType() == ChatEvent.ChatEventType.Completed) {
                metadataAtomicReference.set(((ChatEvent.ChatCompletedEvent) it)
                        .getChatResponse().metadata());
            }
        }).filter(it -> it.getEventType() == ChatEvent.ChatEventType.PartialResponse)
                .map(it -> ((ChatEvent.PartialResponseEvent) it).getChunk())
                .plug(actual::accumulate)
                .map(s -> new ChatEvent.AccumulatedResponseEvent(s,
                        Optional.ofNullable(metadataAtomicReference.get()).orElse(ChatResponseMetadata.builder().build())));

    }

    /**
     * @deprecated Deprecated in favor of upstream implementation
     */
    @Deprecated(forRemoval = true)
    public static OutputGuardrailResult invokeOutputGuardrailsForStream(AiServiceMethodCreateInfo methodCreateInfo,
            OutputGuardrailParams outputGuardrailParams, BeanManager beanManager, AuditSourceInfo auditSourceInfo) {
        return invokeOutputGuardRails(methodCreateInfo, outputGuardrailParams, beanManager, auditSourceInfo);
    }

    /**
     * @deprecated Deprecated in favor of upstream implementation
     */
    @Deprecated(forRemoval = true)
    static class GuardrailRetryException extends RuntimeException {
        // Marker class to indicate a retry to the downstream consumer.
    }
}
