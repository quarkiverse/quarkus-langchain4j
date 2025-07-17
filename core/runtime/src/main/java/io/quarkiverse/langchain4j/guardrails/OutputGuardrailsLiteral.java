package io.quarkiverse.langchain4j.guardrails;

import java.util.List;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.runtime.config.GuardrailsConfig;

public final class OutputGuardrailsLiteral extends ClassProvidingAnnotationLiteral<OutputGuardrails, OutputGuardrail>
        implements OutputGuardrails {
    private int maxRetries;

    /**
     * Needed because this class will be serialized & deserialized
     */
    public OutputGuardrailsLiteral() {
        this(List.of());
    }

    public OutputGuardrailsLiteral(List<String> guardrailsClasses) {
        this(guardrailsClasses, GuardrailsConfig.MAX_RETRIES_DEFAULT);
    }

    public OutputGuardrailsLiteral(List<String> guardrailsClasses, int maxRetries) {
        super(guardrailsClasses);
        this.maxRetries = maxRetries;
    }

    /**
     * Needed because this class will be serialized & deserialized
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Needed because this class will be serialized & deserialized
     */
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public int maxRetries() {
        return this.maxRetries;
    }
}
