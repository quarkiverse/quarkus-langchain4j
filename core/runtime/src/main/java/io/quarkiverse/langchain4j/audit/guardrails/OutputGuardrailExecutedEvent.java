package io.quarkiverse.langchain4j.audit.guardrails;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;

/**
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public interface OutputGuardrailExecutedEvent
        extends GuardrailExecutedEvent<OutputGuardrailRequest, OutputGuardrailResult, OutputGuardrail> {
}
