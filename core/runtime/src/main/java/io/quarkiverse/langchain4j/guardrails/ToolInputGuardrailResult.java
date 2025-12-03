package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * Result of tool input guardrail validation.
 * <p>
 * This record represents the outcome of input validation, indicating whether the tool
 * execution should proceed, be modified, or be rejected. Guardrails return instances
 * of this class to communicate their validation decision.
 * </p>
 *
 * <p>
 * There are four possible outcomes:
 * </p>
 * <ul>
 * <li><strong>Success:</strong> Validation passed, proceed with the original request</li>
 * <li><strong>Success with modification:</strong> Validation passed with a modified request</li>
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
 * return ToolInputGuardrailResult.success();
 *
 * // Validation fails with message to LLM
 * return ToolInputGuardrailResult.failure("Invalid email format");
 *
 * // Fatal failure with exception
 * return ToolInputGuardrailResult.failure("Unauthorized", new SecurityException());
 *
 * // Modify the request (e.g., sanitize input)
 * ToolExecutionRequest modified = ToolExecutionRequest.builder()
 *         .name(request.toolName())
 *         .arguments(sanitizedArgs)
 *         .build();
 * return ToolInputGuardrailResult.successWith(modified);
 * }
 * </pre>
 *
 * @param isSuccess true if validation passed, false otherwise
 * @param errorMessage error message to return to the LLM (null if success)
 * @param cause exception that caused the failure (null unless fatal failure)
 * @param modifiedRequest modified tool execution request (null unless request was modified)
 *
 * @see ToolInputGuardrail
 * @see ToolInputGuardrailRequest
 */
public record ToolInputGuardrailResult(
        boolean isSuccess,
        String errorMessage,
        Throwable cause,
        boolean isFatalFailure,
        ToolExecutionRequest modifiedRequest) implements ToolGuardrailResult<ToolInputGuardrailResult> {

    /**
     * Creates a successful validation result.
     * <p>
     * The tool execution will proceed with the original request.
     * </p>
     *
     * @return a success result
     */
    public static ToolInputGuardrailResult success() {
        return new ToolInputGuardrailResult(true, null, null, false, null);
    }

    /**
     * Creates a successful validation result with a modified request.
     * <p>
     * The tool execution will proceed with the modified request instead of the original.
     * This is useful for input sanitization or transformation.
     * </p>
     *
     * @param modifiedRequest the modified tool execution request to use
     * @return a success result with request modification
     */
    public static ToolInputGuardrailResult successWith(ToolExecutionRequest modifiedRequest) {
        return new ToolInputGuardrailResult(true, null, null, false, modifiedRequest);
    }

    /**
     * Creates a failure result with an error message.
     * <p>
     * The tool execution will be skipped, and the error message will be returned to the LLM
     * as the tool result. This is a non-fatal failure that allows the conversation to continue.
     * </p>
     *
     * @param errorMessage the error message to return to the LLM
     * @return a failure result
     */
    public static ToolInputGuardrailResult failure(String errorMessage) {
        return new ToolInputGuardrailResult(false, errorMessage, null, false, null);
    }

    /**
     * Creates a fatal failure result with an error message.
     * <p>
     * The tool execution will be skipped, and the exception will be thrown, stopping
     * the conversation. This should be used for critical failures that cannot be recovered from,
     * such as authorization failures or system errors.
     * </p>
     *
     * @param failure the exception that caused the failure
     * @return a fatal failure result
     */
    public static ToolInputGuardrailResult fatal(Throwable failure) {
        return new ToolInputGuardrailResult(false, failure.getMessage(), failure.getCause(), true, null);
    }

    /**
     * Creates a fatal failure result with an error message and exception.
     * <p>
     * The tool execution will be skipped, and the exception will be thrown, stopping
     * the conversation. This should be used for critical failures that cannot be recovered from,
     * such as authorization failures or system errors.
     * </p>
     *
     * @param errorMessage the error message describing the failure
     * @param cause the exception that caused the failure
     * @return a fatal failure result
     */
    public static ToolInputGuardrailResult fatal(String errorMessage, Throwable cause) {
        return new ToolInputGuardrailResult(false, errorMessage, cause, true, null);
    }
}
