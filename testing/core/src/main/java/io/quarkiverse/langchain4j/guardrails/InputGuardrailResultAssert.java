package io.quarkiverse.langchain4j.guardrails;

import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult.Failure;

/**
 * Custom assertions for {@link InputGuardrailResult}s
 * <p>
 * This follows the pattern described in https://assertj.github.io/doc/#assertj-core-custom-assertions-creation
 * </p>
 *
 * @deprecated Use {@link dev.langchain4j.test.guardrail.InputGuardrailResultAssert} instead
 */
@Deprecated(forRemoval = true)
public final class InputGuardrailResultAssert
        extends GuardrailResultAssert<InputGuardrailResultAssert, InputGuardrailResult, Failure> {

    private InputGuardrailResultAssert(InputGuardrailResult inputGuardrailResult) {
        super(inputGuardrailResult, InputGuardrailResultAssert.class, Failure.class);
    }

    public static InputGuardrailResultAssert assertThat(InputGuardrailResult actual) {
        return new InputGuardrailResultAssert(actual);
    }
}
