package io.quarkiverse.langchain4j.guardrails;

/**
 * Result of tool output guardrail validation.
 * <p>
 * This record represents the outcome of output validation, indicating whether the tool result
 * should be returned as-is, be modified, or be rejected. Guardrails return
 * instances of this class to communicate their validation decision.
 * </p>
 *
 * <p>
 * There are four possible outcomes:
 * </p>
 * <ul>
 * <li><strong>Success:</strong> Validation passed, return the original result to the LLM</li>
 * <li><strong>Success with modification:</strong> Validation passed with a modified result</li>
 * <li><strong>Failure:</strong> Validation failed, return error message to the LLM</li>
 * <li><strong>Fatal failure:</strong> Critical validation failure with exception</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * // Validation passes
 * return ToolOutputGuardrailResult.success();
 *
 * // Transform the result (e.g., filter sensitive data)
 * ToolExecutionResult filtered = ToolExecutionResult.builder()
 *         .resultText(filterSensitiveData(result.resultText()))
 *         .build();
 * return ToolOutputGuardrailResult.successWith(filtered);
 *
 * // Validation fails with message to LLM
 * return ToolOutputGuardrailResult.failure("Output validation failed");
 *
 * // Fatal failure with exception
 * return ToolOutputGuardrailResult.failure("Critical error", exception);
 * }
 * </pre>
 *
 *
 * @param isSuccess true if validation passed, false otherwise
 * @param errorMessage error message to return to the LLM (null if success)
 * @param cause exception that caused the failure (null unless fatal failure)
 * @param modifiedResult modified tool execution result (null unless result was modified)
 * @see ToolOutputGuardrail
 * @see ToolOutputGuardrailRequest
 */
public record ToolOutputGuardrailResult(
        boolean isSuccess,
        String errorMessage,
        Throwable cause,
        boolean isFatalFailure,
        dev.langchain4j.service.tool.ToolExecutionResult modifiedResult)
        implements
            ToolGuardrailResult<ToolOutputGuardrailResult> {

    /**
     * Creates a successful validation result.
     * <p>
     * The tool result will be returned to the LLM as-is.
     * </p>
     *
     * @return a success result
     */
    public static ToolOutputGuardrailResult success() {
        return new ToolOutputGuardrailResult(true, null, null, false, null);
    }

    /**
     * Creates a successful validation result with a modified result.
     * <p>
     * The modified result will be returned to the LLM instead of the original.
     * This is useful for filtering sensitive data, truncating large outputs,
     * or transforming the result format.
     * </p>
     *
     * @param modifiedResult the modified tool execution result to return
     * @return a success result with result modification
     */
    public static ToolOutputGuardrailResult successWith(dev.langchain4j.service.tool.ToolExecutionResult modifiedResult) {
        return new ToolOutputGuardrailResult(true, null, null, false, modifiedResult);
    }

    /**
     * Creates a failure result with an error message.
     * <p>
     * The error message will be returned to the LLM as the tool result instead of the
     * actual output. This is a non-fatal failure that allows the conversation to continue.
     * </p>
     *
     * @param errorMessage the error message to return to the LLM
     * @return a failure result
     */
    public static ToolOutputGuardrailResult failure(String errorMessage) {
        return new ToolOutputGuardrailResult(false, errorMessage, null, false, null);
    }

    /**
     * Creates a fatal failure result with an error message and exception.
     * <p>
     * The exception will be thrown, stopping the conversation. This should be used for
     * critical failures that cannot be recovered from, such as system errors or security violations.
     * </p>
     *
     * @param errorMessage the error message describing the failure
     * @param cause the exception that caused the failure
     * @return a fatal failure result
     */
    public static ToolOutputGuardrailResult fatal(String errorMessage, Throwable cause) {
        return new ToolOutputGuardrailResult(false, errorMessage, cause, true, null);
    }

    /**
     * Creates a fatal failure result with an error message and exception.
     * <p>
     * The exception will be thrown, stopping the conversation. This should be used for
     * critical failures that cannot be recovered from, such as system errors or security violations.
     * </p>
     *
     * @param failure the exception that caused the failure
     * @return a fatal failure result
     */
    public static ToolOutputGuardrailResult fatal(Throwable failure) {
        return new ToolOutputGuardrailResult(false, failure.getMessage(), failure.getCause(), true, null);
    }
}
