package io.quarkiverse.langchain4j.guardrails;

/**
 * Exception thrown when tool guardrail validation fails critically.
 * <p>
 * This exception is thrown in the following scenarios:
 * </p>
 * <ul>
 * <li>An input guardrail returns {@link ToolInputGuardrailResult#failure(String, Throwable)}
 * indicating a fatal validation failure</li>
 * <li>An output guardrail returns {@link ToolOutputGuardrailResult#failure(String, Throwable)}
 * indicating a fatal validation failure</li>
 * <li>A guardrail bean cannot be looked up via CDI</li>
 * </ul>
 * <p>
 * For non-fatal validation failures, guardrails should return a failure result without
 * a cause exception, which will result in an error message being returned to the LLM
 * rather than throwing this exception.
 * </p>
 *
 * @see ToolInputGuardrail
 * @see ToolOutputGuardrail
 * @see ToolInputGuardrailResult
 * @see ToolOutputGuardrailResult
 */
public class ToolGuardrailException extends RuntimeException {

    /**
     * Constructs a new tool guardrail exception with the specified detail message.
     *
     * @param message the detail message
     */
    public ToolGuardrailException(String message) {
        super(message);
    }

    /**
     * Constructs a new tool guardrail exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public ToolGuardrailException(String message, Throwable cause) {
        super(message, cause);
    }
}
