package io.quarkiverse.langchain4j.guardrails;

import java.util.List;

/**
 * The result of the validation of an interaction between a user and the LLM.
 */
public interface GuardrailResult<GR extends GuardrailResult> {

    /**
     * The possible results of a guardrails validation.
     */
    enum Result {
        /**
         * A successful validation.
         */
        SUCCESS,
        /**
         * A failed validation not preventing the subsequent validations eventually registered to be evaluated.
         */
        FAILURE,
        /**
         * A fatal failed validation, blocking the evaluation of any other validations eventually registered.
         */
        FATAL
    }

    boolean isSuccess();

    boolean isFatal();

    /**
     * @return The list of failures eventually resulting from a set of validations.
     */
    List<? extends Failure> failures();

    default Throwable getFirstFailureException() {
        if (!isSuccess()) {
            for (Failure failure : failures()) {
                if (failure.cause() != null) {
                    return failure.cause();
                }
            }
        }
        return null;
    }

    GR validatedBy(Class<? extends Guardrail> guardrailClass);

    /**
     * The message and the cause of the failure of a single validation.
     */
    interface Failure {
        Failure withGuardrailClass(Class<? extends Guardrail> guardrailClass);

        String message();

        Throwable cause();
    }
}
