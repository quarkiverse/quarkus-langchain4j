package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.data.message.UserMessage.userMessage;

import java.util.*;
import java.util.function.Function;

import jakarta.enterprise.inject.spi.CDI;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.AugmentationResult;
import io.quarkiverse.langchain4j.guardrails.Guardrail;
import io.quarkiverse.langchain4j.guardrails.GuardrailParams;
import io.quarkiverse.langchain4j.guardrails.GuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputTokenAccumulator;
import io.smallrye.mutiny.Multi;

public class GuardrailsSupport {

    public static void invokeInputGuardrails(AiServiceMethodCreateInfo methodCreateInfo, UserMessage userMessage,
            ChatMemory chatMemory, AugmentationResult augmentationResult, Map<String, Object> templateParams) {
        InputGuardrailResult result;
        try {

            Optional<String> userMessageTemplateOpt = methodCreateInfo.getUserMessageInfo().template()
                    .flatMap(AiServiceMethodCreateInfo.TemplateInfo::text);

            String userMessageTemplate = userMessageTemplateOpt.orElse(null);

            result = invokeInputGuardRails(methodCreateInfo,
                    new InputGuardrailParams(userMessage, chatMemory, augmentationResult, promptTemplate, templateParams));
        } catch (Exception e) {
            throw new GuardrailException(e.getMessage(), e);
        }
        if (!result.isSuccess()) {
            throw new GuardrailException(result.toString(), result.getFirstFailureException());
        }
    }

    public static Response<AiMessage> invokeOutputGuardrails(AiServiceMethodCreateInfo methodCreateInfo,
            ChatMemory chatMemory,
            ChatLanguageModel chatModel,
            Response<AiMessage> response,
            List<ToolSpecification> toolSpecifications,
            OutputGuardrailParams output) {
        int attempt = 0;
        int max = methodCreateInfo.getGuardrailsMaxRetry();
        if (max <= 0) {
            max = 1;
        }
        while (attempt < max) {
            OutputGuardrailResult result;
            try {
                result = invokeOutputGuardRails(methodCreateInfo, output);
            } catch (Exception e) {
                throw new GuardrailException(e.getMessage(), e);
            }

            if (!result.isSuccess()) {
                if (!result.isRetry()) {
                    throw new GuardrailException(result.toString(), result.getFirstFailureException());
                } else if (result.getReprompt() != null) {
                    // Retry with re-prompting
                    chatMemory.add(userMessage(result.getReprompt()));
                    if (toolSpecifications == null) {
                        response = chatModel.generate(chatMemory.messages());
                    } else {
                        response = chatModel.generate(chatMemory.messages(), toolSpecifications);
                    }
                    chatMemory.add(response.content());
                } else {
                    // Retry without re-prompting
                    if (toolSpecifications == null) {
                        response = chatModel.generate(chatMemory.messages());
                    } else {
                        response = chatModel.generate(chatMemory.messages(), toolSpecifications);
                    }
                    chatMemory.add(response.content());
                }
                attempt++;
                output = new OutputGuardrailParams(response.content(), output.memory(),
                        output.augmentationResult());
            } else {
                break;
            }
        }

        if (attempt == max) {
            throw new GuardrailException("Output validation failed. The guardrails have reached the maximum number of retries");
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private static OutputGuardrailResult invokeOutputGuardRails(AiServiceMethodCreateInfo methodCreateInfo,
            OutputGuardrailParams params) {
        if (methodCreateInfo.getOutputGuardrailsClassNames().isEmpty()) {
            return OutputGuardrailResult.success();
        }
        List<Class<? extends OutputGuardrail>> classes;
        synchronized (AiServiceMethodImplementationSupport.class) {
            classes = methodCreateInfo.getOutputGuardrailsClasses();
            if (classes.isEmpty()) {
                for (String className : methodCreateInfo.getOutputGuardrailsClassNames()) {
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

        return guardrailResult(params, (List) classes, OutputGuardrailResult.success(), OutputGuardrailResult::failure);
    }

    @SuppressWarnings("unchecked")
    private static InputGuardrailResult invokeInputGuardRails(AiServiceMethodCreateInfo methodCreateInfo,
            InputGuardrailParams params) {
        if (methodCreateInfo.getInputGuardrailsClassNames().isEmpty()) {
            return InputGuardrailResult.success();
        }
        List<Class<? extends InputGuardrail>> classes;
        synchronized (AiServiceMethodImplementationSupport.class) {
            classes = methodCreateInfo.getInputGuardrailsClasses();
            if (classes.isEmpty()) {
                for (String className : methodCreateInfo.getInputGuardrailsClassNames()) {
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

        return guardrailResult(params, (List) classes, InputGuardrailResult.success(), InputGuardrailResult::failure);
    }

    private static <GR extends GuardrailResult> GR guardrailResult(GuardrailParams params,
            List<Class<? extends Guardrail>> classes, GR accumulatedResults,
            Function<List<? extends GuardrailResult.Failure>, GR> producer) {
        for (Class<? extends Guardrail> bean : classes) {
            GR result = (GR) CDI.current().select(bean).get().validate(params).validatedBy(bean);
            if (result.isFatal()) {
                return result;
            }
            accumulatedResults = compose(accumulatedResults, result, producer);
        }

        return accumulatedResults;
    }

    private static <GR extends GuardrailResult> GR compose(GR first, GR second,
            Function<List<? extends GuardrailResult.Failure>, GR> producer) {
        if (first.isSuccess()) {
            return second;
        }
        if (second.isSuccess()) {
            return first;
        }
        List<? extends GuardrailResult.Failure> failures = new ArrayList<>();
        failures.addAll(first.failures());
        failures.addAll(second.failures());
        return producer.apply(failures);
    }

    public static Multi<String> accumulate(Multi<String> upstream, AiServiceMethodCreateInfo methodCreateInfo) {
        if (methodCreateInfo.getOutputGuardrailsClassNames().isEmpty()) {
            return upstream;
        }
        OutputTokenAccumulator accumulator;
        synchronized (AiServiceMethodImplementationSupport.class) {
            accumulator = methodCreateInfo.getOutputTokenAccumulator();
            if (accumulator == null) {
                String cn = methodCreateInfo.getOutputTokenAccumulatorClassName();
                if (cn == null) {
                    return upstream.collect().in(StringBuilder::new, StringBuilder::append)
                            .map(StringBuilder::toString)
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
        return upstream.plug(s -> actual.accumulate(upstream));
    }

    public static OutputGuardrailResult invokeOutputGuardrailsForStream(AiServiceMethodCreateInfo methodCreateInfo,
            OutputGuardrailParams outputGuardrailParams) {
        return invokeOutputGuardRails(methodCreateInfo, outputGuardrailParams);
    }

    static class GuardrailRetryException extends RuntimeException {
        // Marker class to indicate a retry to the downstream consumer.
    }
}
