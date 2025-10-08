package io.quarkiverse.langchain4j.audit.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * @deprecated In favor of https://docs.langchain4j.dev/tutorials/observability#ai-service-observability
 */
@Deprecated(forRemoval = true)
public interface InputGuardrailExecutedEvent
        extends GuardrailExecutedEvent<InputGuardrailRequest, InputGuardrailResult, InputGuardrail> {
    /**
     * Retrieves a rewritten user message if a successful rewritten result exists.
     * If the result contains a rewritten message, it constructs a new user message
     * with the rewritten text; otherwise, it returns the original user message.
     *
     * @return The rewritten user message if a rewritten result exists; otherwise, the original user message.
     */
    UserMessage rewrittenUserMessage();
}
