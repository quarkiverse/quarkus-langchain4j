package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;

/**
 * Represents the parameter passed to {@link OutputGuardrail#validate(OutputGuardrailParams)}.
 *
 * @param responseFromLLM the response from the LLM
 * @param memory the memory, can be {@code null} or empty
 * @param augmentationResult the augmentation result, can be {@code null}
 */
public record OutputGuardrailParams(AiMessage responseFromLLM, ChatMemory memory,
        AugmentationResult augmentationResult) implements GuardrailParams {
}
