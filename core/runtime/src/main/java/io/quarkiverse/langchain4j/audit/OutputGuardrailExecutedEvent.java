package io.quarkiverse.langchain4j.audit;

import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;

public interface OutputGuardrailExecutedEvent
        extends GuardrailExecutedEvent<OutputGuardrailParams, OutputGuardrailResult, OutputGuardrail> {
}
