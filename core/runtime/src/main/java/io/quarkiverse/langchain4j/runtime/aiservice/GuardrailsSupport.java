package io.quarkiverse.langchain4j.runtime.aiservice;

import static dev.langchain4j.data.message.UserMessage.userMessage;

import java.util.List;

import jakarta.enterprise.inject.spi.CDI;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.AugmentationResult;
import io.quarkiverse.langchain4j.guardrails.GuardrailException;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;

public class GuardrailsSupport {

    public static void invokeInputGuardrails(AiServiceMethodCreateInfo methodCreateInfo, UserMessage userMessage,
            ChatMemory chatMemory, AugmentationResult augmentationResult) {
        InputGuardRailsResult result = invokeInputGuardRails(methodCreateInfo,
                new InputGuardrail.InputGuardrailParams(userMessage, chatMemory, augmentationResult));
        if (!result.success()) {
            throw new GuardrailException(
                    "Input validation failed. The guardrail " + result.bean().getName() + " thrown an exception",
                    result.failure());
        }
    }

    public static Response<AiMessage> invokeOutputGuardrails(AiServiceMethodCreateInfo methodCreateInfo,
            ChatMemory chatMemory,
            ChatLanguageModel chatModel,
            Response<AiMessage> response,
            List<ToolSpecification> toolSpecifications,
            OutputGuardrail.OutputGuardrailParams output) {
        int attempt = 0;
        int max = methodCreateInfo.getGuardrailsMaxRetry();
        if (max <= 0) {
            max = 1;
        }
        while (attempt < max) {
            OutputGuardRailsResult grr = invokeOutputGuardRails(methodCreateInfo, output);
            if (!grr.success) {
                if (!grr.retry()) {
                    throw new GuardrailException(
                            "Output validation failed. The guardrail " + grr.bean().getName() + " thrown an exception",
                            grr.failure());
                } else if (grr.reprompt() != null) {
                    // Retry with re-prompting
                    chatMemory.add(userMessage(grr.reprompt()));
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
            } else {
                break;
            }
        }

        if (attempt == max) {
            throw new GuardrailException("Output validation failed. The guardrails have reached the maximum number of retries",
                    null);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private static OutputGuardRailsResult invokeOutputGuardRails(AiServiceMethodCreateInfo methodCreateInfo,
            OutputGuardrail.OutputGuardrailParams params) {
        if (methodCreateInfo.getOutputGuardrailsClassNames().isEmpty()) {
            return OutputGuardRailsResult.SUCCESS;
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

        for (Class<? extends OutputGuardrail> bean : classes) {
            try {
                CDI.current().select(bean).get().validate(params);
            } catch (OutputGuardrail.ValidationException e) {
                return new OutputGuardRailsResult(false, bean, e, e.isRetry(), e.getReprompt());
            } catch (Exception e) {
                return new OutputGuardRailsResult(false, bean, e, false, null);
            }
        }

        return OutputGuardRailsResult.SUCCESS;
    }

    @SuppressWarnings("unchecked")
    private static InputGuardRailsResult invokeInputGuardRails(AiServiceMethodCreateInfo methodCreateInfo,
            InputGuardrail.InputGuardrailParams params) {
        if (methodCreateInfo.getInputGuardrailsClassNames().isEmpty()) {
            return InputGuardRailsResult.SUCCESS;
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

        for (Class<? extends InputGuardrail> bean : classes) {
            try {
                CDI.current().select(bean).get().validate(params);
            } catch (Exception e) {
                return new InputGuardRailsResult(false, bean, e);
            }
        }

        return InputGuardRailsResult.SUCCESS;
    }

    private record OutputGuardRailsResult(boolean success, Class<? extends OutputGuardrail> bean, Exception failure,
            boolean retry, String reprompt) {

        static OutputGuardRailsResult SUCCESS = new OutputGuardRailsResult(true, null, null, false, null);

    }

    private record InputGuardRailsResult(boolean success, Class<? extends InputGuardrail> bean, Exception failure) {

        static InputGuardRailsResult SUCCESS = new InputGuardRailsResult(true, null, null);

    }
}
