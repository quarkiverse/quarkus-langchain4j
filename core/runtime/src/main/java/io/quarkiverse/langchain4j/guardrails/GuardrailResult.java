package io.quarkiverse.langchain4j.guardrails;

import java.util.List;

/**
 * The result of the validation of an interaction between a user and the LLM.
 *
 * @deprecated Use {@link dev.langchain4j.guardrail.GuardrailResult} instead
 */
@Deprecated(forRemoval = true)
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
         * A successful validation with a specific result.
         */
        SUCCESS_WITH_RESULT,
        /**
         * A failed validation not preventing the subsequent validations eventually registered to be evaluated.
         */
        FAILURE,
        /**
         * A fatal failed validation, blocking the evaluation of any other validations eventually registered.
         */
        FATAL
    }

    Result getResult();

    default boolean isSuccess() {
        return getResult() == Result.SUCCESS || getResult() == Result.SUCCESS_WITH_RESULT;
    }

    default boolean hasRewrittenResult() {
        return getResult() == Result.SUCCESS_WITH_RESULT;
    }

    default GuardrailResult<GR> blockRetry() {
        throw new UnsupportedOperationException();
    }

    default String successfulText() {
        throw new UnsupportedOperationException();
    }

    default Object successfulResult() {
        throw new UnsupportedOperationException();
    }

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

        Class<? extends Guardrail> guardrailClass();
    }
}
