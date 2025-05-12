package io.quarkiverse.langchain4j.audit.internal;

import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.OutputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;

public record DefaultOutputGuardrailExecutedEvent(AuditSourceInfo sourceInfo, OutputGuardrailParams params,
        OutputGuardrailResult result, Class<OutputGuardrail> guardrailClass) implements OutputGuardrailExecutedEvent {
}