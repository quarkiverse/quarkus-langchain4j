package io.quarkiverse.langchain4j.guardrails;

import org.assertj.core.api.Assertions;

/**
 * Custom assertions for working with Guardrails
 * <p>
 * This follows the pattern described in https://assertj.github.io/doc/#assertj-core-custom-assertions-entry-point
 * </p>
 */
public class GuardrailAssertions extends Assertions {
    public static OutputGuardrailResultAssert assertThat(OutputGuardrailResult actual) {
        return OutputGuardrailResultAssert.assertThat(actual);
    }
}
