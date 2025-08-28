package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;

/**
 * Represents the parameter passed to {@link Guardrail#validate(GuardrailParams)}} in order to validate an interaction between a
 * user and the LLM.
 *
 * @deprecated Use {@link dev.langchain4j.guardrail.GuardrailRequestParams} instead
 */
@Deprecated(forRemoval = true)
public interface GuardrailParams {

    /**
     * @return the memory, can be {@code null} or empty
     */
    ChatMemory memory();

    /**
     * @return the augmentation result, can be {@code null}
     */
    AugmentationResult augmentationResult();

    /**
     * Recreate this guardrail param with the given input or output text.
     *
     * @param text The text of the rewritten param.
     * @return A clone of this guardrail params with the given input or output text.
     */
    GuardrailParams withText(String text);
}
