package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.vertx.core.json.JsonObject;

/**
 * Request object for tool output guardrails containing all context needed for validation.
 * <p>
 * This record provides complete information about the tool execution that has occurred,
 * including the result, the original request, tool metadata, and invocation context.
 * Guardrails can use this information to validate outputs, filter sensitive data,
 * or transform results before they are returned to the LLM.
 * </p>
 *
 * <p>
 * Example usage in a guardrail:
 * </p>
 *
 * <pre>
 * {@code
 * @Override
 * public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
 *     String result = request.resultText();
 *     boolean hasError = request.isError();
 *
 *     // Validate or transform the result
 *     if (result.length() > MAX_SIZE) {
 *         String truncated = result.substring(0, MAX_SIZE) + "...";
 *         ToolExecutionResult modified = ToolExecutionResult.builder()
 *                 .resultText(truncated)
 *                 .build();
 *         return ToolOutputGuardrailResult.successWith(modified);
 *     }
 *
 *     return ToolOutputGuardrailResult.success();
 * }
 * }
 * </pre>
 *
 * @param executionResult the tool execution result containing output text and error status
 * @param executionRequest the original tool execution request from the LLM
 * @param toolMetadata metadata about the tool including specification and instance
 * @param invocationContext the invocation context containing memory ID and custom parameters
 * @see ToolOutputGuardrail
 * @see dev.langchain4j.service.tool.ToolExecutionResult
 * @see ToolExecutionRequest
 * @see ToolMetadata
 * @see ToolInvocationContext
 */
public record ToolOutputGuardrailRequest(
        dev.langchain4j.service.tool.ToolExecutionResult executionResult,
        ToolExecutionRequest executionRequest,
        ToolMetadata toolMetadata,
        ToolInvocationContext invocationContext) implements ToolGuardrailRequest<ToolOutputGuardrailRequest> {

    /**
     * Constructor for validation.
     *
     * @param executionResult the tool execution result (must not be null)
     * @param executionRequest the original tool execution request (can be null)
     * @param toolMetadata the tool metadata (can be null)
     * @param invocationContext the invocation context (can be null)
     * @throws IllegalArgumentException if executionResult is null
     */
    public ToolOutputGuardrailRequest {
        if (executionResult == null) {
            throw new IllegalArgumentException("executionResult cannot be null");
        }
    }

    /**
     * Convenience method to get the tool name from the execution request.
     *
     * @return the tool name, or null if no execution request
     */
    public String toolName() {
        return executionRequest != null ? executionRequest.name() : null;
    }

    /**
     * Convenience method to get the result text from the execution result.
     *
     * @return the result text
     */
    public String resultText() {
        return executionResult.resultText();
    }

    /**
     * Convenience method to check if the execution resulted in an error.
     * <p>
     * When an error occurs, use {@link #resultText()} to get the error message.
     * The original exception is not available in ToolExecutionResult.
     * </p>
     *
     * @return true if the execution failed, false otherwise
     */
    public boolean isError() {
        return executionResult.isError();
    }

    /**
     * Convenience method to get the memory ID from the invocation context.
     *
     * @return the memory ID, or null if no invocation context
     */
    public Object memoryId() {
        return invocationContext != null ? invocationContext.memoryId() : null;
    }

    /**
     * Parses the tool arguments as a Vert.x JsonObject for easier parameter access.
     * <p>
     * This provides access to the original tool arguments that produced this output,
     * enabling guardrails to validate the relationship between inputs and outputs.
     * </p>
     *
     * <p>
     * Example usage:
     * </p>
     *
     * <pre>
     * {@code
     * // Check if output matches input expectations
     * JsonObject args = request.argumentsAsJson();
     * int expectedValue = args.getInteger("expectedMax");
     *
     * int actualValue = parseResult(request.resultText());
     * if (actualValue > expectedValue) {
     *     return ToolOutputGuardrailResult.failure("Result exceeds expected maximum");
     * }
     * }
     * </pre>
     *
     * @return the arguments as a Vert.x JsonObject, or null if no execution request
     * @throws io.vertx.core.json.DecodeException if the arguments string is not valid JSON
     * @see JsonObject
     */
    public JsonObject argumentsAsJson() {
        return executionRequest != null ? new JsonObject(executionRequest.arguments()) : null;
    }

}
