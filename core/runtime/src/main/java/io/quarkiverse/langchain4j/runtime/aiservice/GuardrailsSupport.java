package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import jakarta.enterprise.inject.spi.CDI;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.service.guardrail.GuardrailService;
import io.quarkiverse.langchain4j.guardrails.NoopChatExecutor;
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
                                    .invocationContext(InvocationContext.builder()
                                            .interfaceName(methodCreateInfo.getInterfaceName())
                                            .methodName(methodCreateInfo.getMethodName())
                                            .chatMemoryId(chatMemory.id())
                                            .build())
                                    .build())
                    .build();

            um = guardrailService.executeGuardrails(methodCreateInfo, request);
        }

        return um;
    }

    static UserMessage executeInputGuardrails(GuardrailService guardrailService, UserMessage userMessage,
            AiServiceMethodCreateInfo methodCreateInfo, ChatMemory chatMemory, AugmentationResult augmentationResult,
            Map<String, Object> templateVariables, InvocationContext invocationContext) {
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
                                    .invocationContext(invocationContext)
                                    .build())
                    .build();

            um = guardrailService.executeGuardrails(methodCreateInfo, request);
        }

        return um;
    }

    static <T> T executeOutputGuardrails(GuardrailService guardrailService, AiServiceMethodCreateInfo methodCreateInfo,
            ChatResponse response, ChatExecutor chatExecutor, CommittableChatMemory committableChatMemory,
            AugmentationResult augmentationResult, Map<String, Object> templateVariables) {

        T result = null;

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
                                    .invocationContext(InvocationContext.builder()
                                            .interfaceName(methodCreateInfo.getInterfaceName())
                                            .methodName(methodCreateInfo.getMethodName())
                                            .chatMemoryId(committableChatMemory.id())
                                            .build())
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
                        templateVariables);

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
                    templateVariables);

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
}
