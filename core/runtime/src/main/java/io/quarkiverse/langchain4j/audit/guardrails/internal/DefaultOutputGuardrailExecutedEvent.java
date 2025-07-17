package io.quarkiverse.langchain4j.audit.guardrails.internal;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.guardrails.OutputGuardrailExecutedEvent;

public record DefaultOutputGuardrailExecutedEvent(AuditSourceInfo sourceInfo, OutputGuardrailRequest request,
        OutputGuardrailResult result, Class<OutputGuardrail> guardrailClass) implements OutputGuardrailExecutedEvent {
}
