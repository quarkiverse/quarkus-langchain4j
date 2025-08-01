package io.quarkiverse.langchain4j.guardrails;

import java.util.List;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.runtime.config.GuardrailsConfig;

public final class OutputGuardrailsLiteral extends ClassProvidingAnnotationLiteral<OutputGuardrails, OutputGuardrail>
        implements OutputGuardrails {

    private int maxRetriesToPerform;
    private int maxRetriesAsSetByConfig;

    /**
     * Needed because this class will be serialized & deserialized
     */
    public OutputGuardrailsLiteral() {
        this(List.of(), GuardrailsConfig.MAX_RETRIES_DEFAULT, GuardrailsConfig.MAX_RETRIES_DEFAULT);
    }

    public OutputGuardrailsLiteral(List<String> guardrailsClasses, int maxRetries) {
        this(guardrailsClasses, maxRetries, maxRetries);
    }

    /**
     *
     * @param guardrailsClasses The guardrail classes
     * @param maxRetriesToPerform How many retries we want the {@link dev.langchain4j.service.guardrail.GuardrailService} to
     *        perform
     * @param maxRetriesAsSetByConfig The actual number of max retries as set on the annotation. Used in case the method's
     *        return type is {@link io.smallrye.mutiny.Multi}.
     */
    public OutputGuardrailsLiteral(List<String> guardrailsClasses, int maxRetriesToPerform, int maxRetriesAsSetByConfig) {
        super(guardrailsClasses);
        this.maxRetriesToPerform = maxRetriesToPerform;
        this.maxRetriesAsSetByConfig = maxRetriesAsSetByConfig;
    }

    /**
     * Needed because this class will be serialized & deserialized
     */
    public int getMaxRetriesToPerform() {
        return maxRetriesToPerform;
    }

    /**
     * Needed because this class will be serialized & deserialized
     */
    public void setMaxRetriesToPerform(int maxRetriesToPerform) {
        this.maxRetriesToPerform = maxRetriesToPerform;
    }

    @Override
    public int maxRetries() {
        return this.maxRetriesToPerform;
    }

    public int getMaxRetriesAsSetByConfig() {
        return maxRetriesAsSetByConfig;
    }

    /**
     * Needed because this class will be serialized & deserialized
     */
    public void setMaxRetriesAsSetByConfig(int maxRetriesAsSetByConfig) {
        this.maxRetriesAsSetByConfig = maxRetriesAsSetByConfig;
    }
}
