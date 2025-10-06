package io.quarkiverse.langchain4j.audit.guardrails.internal;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.guardrails.InputGuardrailExecutedEvent;

/**
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public record DefaultInputGuardrailExecutedEvent(AuditSourceInfo sourceInfo, InputGuardrailRequest request,
        InputGuardrailResult result, Class<InputGuardrail> guardrailClass) implements InputGuardrailExecutedEvent {

    @Override
    public UserMessage rewrittenUserMessage() {
        return result.hasRewrittenResult() ? request().withText(result.successfulText()).userMessage() : request.userMessage();
    }
}
