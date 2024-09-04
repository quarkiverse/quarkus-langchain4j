package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.rag.AugmentationResult;
import io.smallrye.common.annotation.Experimental;

/**
 * An output guardrail is a rule that is applied to the output of the model to ensure that the output is safe and meets the
 * expectations.
 * <p>
 * Implementation should be exposed as a CDI bean, and the class name configured in {@link OutputGuardrails#value()} annotation.
 * <p>
 * Implementation should throw a {@link ValidationException} when the validation fails. The exception can indicate whether the
 * request should be retried and provide a {@code reprompt} message.
 * In the case of reprompting, the reprompt message is added to the LLM context and the request is retried.
 * <p>
 * The maximum number of retries is configurable using {@code quarkus.langchain4j.guardrails.max-retries}, defaulting to 3.
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface OutputGuardrail {

    /**
     * An exception thrown when the validation fails.
     */
    class ValidationException extends Exception {

        private final boolean retry;
        private final String reprompt;

        /**
         * Creates a new instance of {@link ValidationException} without a cause.
         *
         * @param message the error message
         * @param retry whether the request should be retried
         * @param reprompt if the request should be retried, the reprompt message. If null, the original request
         *        with the same context will be retried.
         */
        public ValidationException(String message, boolean retry, String reprompt) {
            super(message);
            this.retry = retry;
            this.reprompt = reprompt;
            if (reprompt != null && !retry) {
                throw new IllegalArgumentException("Reprompt message is only allowed if retry is true");
            }
        }

        /**
         * Creates a new instance of {@link ValidationException} with a cause.
         *
         * @param message the error message
         * @param cause the cause
         * @param retry whether the request should be retried
         * @param reprompt if the request should be retried, the reprompt message. If null, the original request with
         *        the same context will be retried.
         */
        public ValidationException(String message, Throwable cause, boolean retry, String reprompt) {
            super(message, cause);
            this.retry = retry;
            this.reprompt = reprompt;
        }

        /**
         * Whether the request should be retried.
         *
         * @return true if the request should be retried, false otherwise.
         */
        public boolean isRetry() {
            return retry;
        }

        /**
         * The reprompt message.
         * <p>
         * If {@code isRetry()} returns true, the reprompt message (if not {@code null} is added to the LLM context and
         * the request is retried. If the reprompt message is {@code null}, the original request with the same context.
         * <p>
         * If {@code isRetry()} returns false, the reprompt message is ignored.
         *
         * @return the reprompt message, or null if the original request with the same context should be retried.
         */
        public String getReprompt() {
            return reprompt;
        }
    }

    /**
     * Validates the response from the LLM.
     * <p>
     * If the validation fails with an exception that is not a {@link ValidationException}, no retry will be attempted.
     *
     * @param responseFromLLM the response from the LLM
     * @throws ValidationException the exception throws if the validation fails.
     */
    default void validate(AiMessage responseFromLLM) throws ValidationException {
        throw new ValidationException("Validation not implemented", false, null);
    }

    /**
     * Validates the response from the LLM.
     * <p>
     * Unlike {@link #validate(AiMessage)}, this method allows to access the memory and the augmentation result (in the
     * case of a RAG).
     * <p>
     * If the validation fails with an exception that is not a {@link ValidationException}, no retry will be attempted.
     * <p>
     * Implementation must not attempt to write to the memory or the augmentation result.
     *
     * @param params the parameters, including the response from the LLM, the memory (maybe null),
     *        and the augmentation result (maybe null). Cannot be {@code null}
     * @throws ValidationException the exception throws if the validation fails.
     */
    default void validate(OutputGuardrailParams params)
            throws ValidationException {
        validate(params.responseFromLLM());
    }

    /**
     * Represents the parameter passed to {@link #validate(OutputGuardrailParams)}.
     *
     * @param responseFromLLM the response from the LLM
     * @param memory the memory, can be {@code null} or empty
     * @param augmentationResult the augmentation result, can be {@code null}
     */
    record OutputGuardrailParams(AiMessage responseFromLLM, ChatMemory memory, AugmentationResult augmentationResult) {
    }

}
