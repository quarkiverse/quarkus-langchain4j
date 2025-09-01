package io.quarkiverse.langchain4j.audit;

import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;

/**
 * @deprecated This will be replaced with an alternate version when the upstream guardrail implementation is merged
 */
@Deprecated(forRemoval = true)
public interface OutputGuardrailExecutedEvent
        extends GuardrailExecutedEvent<OutputGuardrailParams, OutputGuardrailResult, OutputGuardrail> {
}
