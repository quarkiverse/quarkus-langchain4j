package io.quarkiverse.langchain4j.runtime.aiservice;

/**
 * Exception thrown when an input or output guardrail validation fails.
 * <p>
 * This exception is not intended to be used in guardrail implementation.
 *
 * @deprecated Use {@link dev.langchain4j.guardrail.GuardrailException} instead
 */
@Deprecated(forRemoval = true)
public class GuardrailException extends RuntimeException {
    public GuardrailException(String message) {
        super(message);
    }

    public GuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
