package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;
import io.smallrye.common.annotation.Experimental;

/**
 * An input guardrail is a rule that is applied to the input of the model to ensure that the input (the user message) is
 * safe and meets the expectations of the model.
 * <p>
 * Implementation should be exposed as a CDI bean, and the class name configured in {@link InputGuardrails#value()} annotation.
 * <p>
 * Implementation should throw a {@link ValidationException} when the validation fails.
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface InputGuardrail {

    /**
     * An exception thrown when the validation fails.
     */
    class ValidationException extends Exception {

        /**
         * Creates a new instance of {@link ValidationException} without a cause.
         *
         * @param message the error message
         */
        public ValidationException(String message) {
            super(message);
        }

        /**
         * Creates a new instance of {@link ValidationException} with a cause.
         *
         * @param message the error message
         * @param cause the cause
         */
        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Validates the {@code user message} that will be sent to the LLM.
     * <p>
     *
     * @param userMessage the response from the LLM
     * @throws ValidationException the exception throws if the validation fails.
     */
    default void validate(UserMessage userMessage) throws ValidationException {
        throw new ValidationException("Validation not implemented");
    }

    /**
     * Validates the input that will be sent to the LLM.
     * <p>
     * Unlike {@link #validate(UserMessage)}, this method allows to access the memory and the augmentation result (in the
     * case of a RAG).
     * <p>
     * Implementation must not attempt to write to the memory or the augmentation result.
     *
     * @param params the parameters, including the user message, the memory (maybe null),
     *        and the augmentation result (maybe null). Cannot be {@code null}
     * @throws ValidationException the exception throws if the validation fails.
     */
    default void validate(InputGuardrailParams params)
            throws ValidationException {
        validate(params.userMessage());
    }

    /**
     * Represents the parameter passed to {@link #validate(InputGuardrailParams)}.
     *
     * @param userMessage the user message, cannot be {@code null}
     * @param memory the memory, can be {@code null} or empty
     * @param augmentationResult the augmentation result, can be {@code null}
     */
    record InputGuardrailParams(UserMessage userMessage, ChatMemory memory, AugmentationResult augmentationResult) {
    }

}
