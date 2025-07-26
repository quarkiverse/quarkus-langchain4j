package io.quarkiverse.langchain4j.audit.internal;

import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.audit.AuditSourceInfo;
import io.quarkiverse.langchain4j.audit.InputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;

/**
 * @deprecated This will be replaced with an alternate version when the upstream guardrail implementation is merged
 */
@Deprecated(forRemoval = true)
public record DefaultInputGuardrailExecutedEvent(AuditSourceInfo sourceInfo, InputGuardrailParams params,
        InputGuardrailResult result, Class<InputGuardrail> guardrailClass) implements InputGuardrailExecutedEvent {

    @Override
    public UserMessage rewrittenUserMessage() {
        return result.hasRewrittenResult() ? params.withText(result.successfulText()).userMessage() : params.userMessage();
    }
}
