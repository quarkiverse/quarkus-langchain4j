package io.quarkiverse.langchain4j.guardrails;

import io.smallrye.mutiny.Multi;

/**
 * Interface to accumulate tokens when output guardrails are applied on streamed responses.
 * Implementation should be CDI beans.
 * They are selected using their classname in the {@link OutputGuardrailAccumulator} annotation.
 */
public interface OutputTokenAccumulator {

    /**
     * Accumulate tokens before applying the guardrails.
     * The guardrails are invoked for each item emitted by the produce {@link Multi}.
     * <p>
     * If the returned {@link Multi} emits an error, the guardrail chain is not called and the error is propagated.
     * If the returned {@link Multi} completes, the guardrail chain is called with the remaining accumulated tokens.
     *
     * @param tokens the input token stream
     * @return the Multi producing the accumulated tokens
     */
    Multi<String> accumulate(Multi<String> tokens);
}
