package io.quarkiverse.langchain4j.audit;

import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;

/**
 * @deprecated This will be replaced with an alternate version when the upstream guardrail implementation is merged
 */
@Deprecated(forRemoval = true)
public interface InputGuardrailExecutedEvent
        extends GuardrailExecutedEvent<InputGuardrailParams, InputGuardrailResult, InputGuardrail> {
    /**
     * Retrieves a rewritten user message if a successful rewritten result exists.
     * If the result contains a rewritten message, it constructs a new user message
     * with the rewritten text; otherwise, it returns the original user message.
     *
     * @return The rewritten user message if a rewritten result exists; otherwise, the original user message.
     */
    UserMessage rewrittenUserMessage();
}
