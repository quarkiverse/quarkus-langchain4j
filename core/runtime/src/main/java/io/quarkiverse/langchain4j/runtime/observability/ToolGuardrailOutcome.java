package io.quarkiverse.langchain4j.runtime.observability;

/**
 * Enum representing the outcome of a tool guardrail execution.
 * Used to ensure consistent tag values in metrics to avoid cardinality issues.
 */
public enum ToolGuardrailOutcome {
    /**
     * Guardrail validation succeeded.
     */
    SUCCESS("success"),

    /**
     * Guardrail validation failed but the LLM can retry
     */
    FAILURE("failure"),

    /**
     * Guardrail validation failed, and the failure is fatal (no retry)
     */
    FATAL("fatal");

    private final String value;

    ToolGuardrailOutcome(String value) {
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
