package io.quarkiverse.langchain4j.audit.guardrails;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;

public interface OutputGuardrailExecutedEvent
        extends GuardrailExecutedEvent<OutputGuardrailRequest, OutputGuardrailResult, OutputGuardrail> {
}
