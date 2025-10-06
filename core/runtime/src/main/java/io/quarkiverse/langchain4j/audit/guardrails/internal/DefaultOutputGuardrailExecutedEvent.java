package io.quarkiverse.langchain4j.audit.guardrails.internal;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.guardrails.OutputGuardrailExecutedEvent;

/**
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public record DefaultOutputGuardrailExecutedEvent(AuditSourceInfo sourceInfo, OutputGuardrailRequest request,
        OutputGuardrailResult result, Class<OutputGuardrail> guardrailClass) implements OutputGuardrailExecutedEvent {
}
