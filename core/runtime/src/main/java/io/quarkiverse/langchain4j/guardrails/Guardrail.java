package io.quarkiverse.langchain4j.guardrails;

/**
 * A guardrail is a rule that is applied when interacting with an LLM either to the input (the user message) or to the output of
 * the model to ensure that they are safe and meet the expectations of the model.
 *
 * @deprecated Use {@link dev.langchain4j.guardrail.Guardrail} instead
 */
@Deprecated(forRemoval = true)
public interface Guardrail<P extends GuardrailParams, R extends GuardrailResult<R>> {

    /**
     * Validate the interaction between the model and the user in one of the two directions.
     *
     * @param params The parameters of the request or the response to be validated.
     * @return The result of this validation.
     */
    R validate(P params);
}
