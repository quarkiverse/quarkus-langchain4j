package io.quarkiverse.langchain4j.guardrails;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The result of the validation of an {@link InputGuardrail}
 *
 * @param result The result of the input guardrail validation.
 * @param failures The list of failures, empty if the validation succeeded.
 */
public record InputGuardrailResult(Result result, String successfulText,
        List<Failure> failures) implements GuardrailResult<InputGuardrailResult> {

    private static final InputGuardrailResult SUCCESS = new InputGuardrailResult();

    private InputGuardrailResult() {
        this(Result.SUCCESS, null, Collections.emptyList());
    }

    private InputGuardrailResult(String successfulText) {
        this(Result.SUCCESS_WITH_RESULT, successfulText, Collections.emptyList());
    }

    InputGuardrailResult(List<Failure> failures, boolean fatal) {
        this(fatal ? Result.FATAL : Result.FAILURE, null, failures);
    }

    public static InputGuardrailResult success() {
        return InputGuardrailResult.SUCCESS;
    }

    public static InputGuardrailResult successWith(String successfulText) {
        return new InputGuardrailResult(successfulText);
    }

    public static InputGuardrailResult failure(List<? extends GuardrailResult.Failure> failures) {
        return new InputGuardrailResult((List<Failure>) failures, false);
    }

    @Override
    public Result getResult() {
        return result;
    }

    @Override
    public boolean isFatal() {
        return result == Result.FATAL;
    }

    @Override
    public InputGuardrailResult validatedBy(Class<? extends Guardrail> guardrailClass) {
        if (!isSuccess()) {
            if (failures.size() != 1) {
                throw new IllegalArgumentException();
            }
            failures.set(0, failures.get(0).withGuardrailClass(guardrailClass));
        }
        return this;
    }

    @Override
    public String toString() {
        if (isSuccess()) {
            return hasRewrittenResult() ? "Success with '" + successfulText + "'" : "Success";
        }
        return failures.stream().map(Failure::toString).collect(Collectors.joining(", "));
    }

    record Failure(String message, Throwable cause,
            Class<? extends Guardrail> guardrailClass) implements GuardrailResult.Failure {
        public Failure(String message) {
            this(message, null);
        }

        public Failure(String message, Throwable cause) {
            this(message, cause, null);
        }

        public Failure withGuardrailClass(Class<? extends Guardrail> guardrailClass) {
            return new Failure(message, cause, guardrailClass);
        }

        @Override
        public String toString() {
            return "The guardrail " + guardrailClass.getName() + " failed with this message: " + message;
        }
    }
}
