package io.quarkiverse.langchain4j.runtime.observability;

/**
 * Enum representing the outcome of a guardrail execution.
 * Used to ensure consistent tag values in metrics to avoid cardinality issues.
 */
public enum GuardrailOutcome {
    /**
     * Guardrail validation succeeded.
     */
    SUCCESS("success"),

    /**
     * Guardrail validation failed.
     */
    FAILURE("failure"),

    /**
     * Guardrail requested a reprompt (output guardrails only).
     */
    REPROMPT("reprompt");

    private final String value;

    GuardrailOutcome(String value) {
        this.value = value;
    }

    /**
     * Returns the string value used in metrics tags.
     *
     * @return the metric tag value
     */
    public String getValue() {
        return value;
    }
}
