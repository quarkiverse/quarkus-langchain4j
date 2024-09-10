package io.quarkiverse.langchain4j.runtime.aiservice;

/**
 * Exception thrown when a input or output guardrail validation fails.
 * <p>
 * This exception is not intended to be used in guardrail implementation.
 */
public class GuardrailException extends RuntimeException {
    public GuardrailException(String message) {
        super(message);
    }

    public GuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
