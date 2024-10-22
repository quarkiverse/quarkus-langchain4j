package io.quarkiverse.langchain4j.guardrails;

import java.util.Arrays;

import dev.langchain4j.data.message.UserMessage;
import io.smallrye.common.annotation.Experimental;

/**
 * An input guardrail is a rule that is applied to the input of the model to ensure that the input (the user message) is
 * safe and meets the expectations of the model.
 * <p>
 * Implementation should be exposed as a CDI bean, and the class name configured in {@link InputGuardrails#value()} annotation.
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface InputGuardrail extends Guardrail<InputGuardrailParams, InputGuardrailResult> {

    /**
     * Validates the {@code user message} that will be sent to the LLM.
     * <p>
     *
     * @param userMessage the response from the LLM
     */
    default InputGuardrailResult validate(UserMessage userMessage) {
        return failure("Validation not implemented");
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
     */
    @Override
    default InputGuardrailResult validate(InputGuardrailParams params) {
        return validate(params.userMessage());
    }

    /**
     * @return The result of a successful input guardrail validation.
     */
    default InputGuardrailResult success() {
        return InputGuardrailResult.success();
    }

    /**
     * @param message A message describing the failure.
     * @return The result of a failed input guardrail validation.
     */
    default InputGuardrailResult failure(String message) {
        return new InputGuardrailResult(Arrays.asList(new InputGuardrailResult.Failure(message)), false);
    }

    /**
     * @param message A message describing the failure.
     * @param cause The exception that caused this failure.
     * @return The result of a failed input guardrail validation.
     */
    default InputGuardrailResult failure(String message, Throwable cause) {
        return new InputGuardrailResult(Arrays.asList(new InputGuardrailResult.Failure(message, cause)), false);
    }

    /**
     * @param message A message describing the failure.
     * @return The result of a fatally failed input guardrail validation, blocking the evaluation of any other subsequent
     *         validation.
     */
    default InputGuardrailResult fatal(String message) {
        return new InputGuardrailResult(Arrays.asList(new InputGuardrailResult.Failure(message)), true);
    }

    /**
     * @param message A message describing the failure.
     * @param cause The exception that caused this failure.
     * @return The result of a fatally failed input guardrail validation, blocking the evaluation of any other subsequent
     *         validation.
     */
    default InputGuardrailResult fatal(String message, Throwable cause) {
        return new InputGuardrailResult(Arrays.asList(new InputGuardrailResult.Failure(message, cause)), true);
    }
}
