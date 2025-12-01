package io.quarkiverse.langchain4j.runtime.observability;

/**
 * Enum representing the type of guardrail.
 * Used to ensure consistent tag values in metrics to avoid cardinality issues.
 */
public enum GuardrailType {
    /**
     * Input guardrail type - validates user messages before sending to LLM.
     */
    INPUT("input"),

    /**
     * Output guardrail type - validates LLM responses before returning to user.
     */
    OUTPUT("output");

    private final String value;

    GuardrailType(String value) {
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
