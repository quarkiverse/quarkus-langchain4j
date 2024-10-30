package io.quarkiverse.langchain4j.guardrails;

import java.util.Map;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;

/**
 * Represents the parameter passed to {@link InputGuardrail#validate(InputGuardrailParams)}.
 *
 * @param userMessage the user message, cannot be {@code null}
 * @param memory the memory, can be {@code null} or empty
 * @param augmentationResult the augmentation result, can be {@code null}
 * @param userMessageTemplate the user message template, cannot be {@code null}
 * @param variables the variable to be used with userMessageTemplate, cannot be {@code null}
 */
public record InputGuardrailParams(UserMessage userMessage, ChatMemory memory,
        AugmentationResult augmentationResult, String userMessageTemplate,
        Map<String, Object> variables) implements GuardrailParams {
}
