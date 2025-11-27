package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.vertx.core.json.JsonObject;

/**
 * Request object for tool input guardrails containing all context needed for validation.
 * <p>
 * This record provides complete information about the tool invocation that is about to occur,
 * including the LLM's request, tool metadata, and invocation context. Guardrails can use this
 * information to validate inputs, check permissions, or make other decisions before tool execution.
 * </p>
 *
 * <p>
 * Example usage in a guardrail:
 * </p>
 *
 * <pre>
 * {@code
 * @Override
 * public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
 *     String toolName = request.toolName();
 *     String arguments = request.arguments();
 *     Object memoryId = request.memoryId();
 *
 *     // Perform validation logic
 *     if (isInvalid(arguments)) {
 *         return ToolInputGuardrailResult.failure("Invalid input");
 *     }
 *
 *     return ToolInputGuardrailResult.success();
 * }
 * }
 * </pre>
 *
 * @param executionRequest the tool execution request from the LLM containing tool name and arguments
 * @param toolMetadata metadata about the tool including specification and instance
 * @param invocationContext the invocation context containing memory ID and custom parameters
 *
 * @see ToolInputGuardrail
 * @see ToolExecutionRequest
 * @see ToolMetadata
 * @see ToolInvocationContext
 */
public record ToolInputGuardrailRequest(
        ToolExecutionRequest executionRequest,
        ToolMetadata toolMetadata,
        ToolInvocationContext invocationContext) implements ToolGuardrailRequest<ToolInputGuardrailRequest> {

    /**
     * Compact constructor for validation.
     *
     * @param executionRequest the tool execution request (must not be null)
     * @param toolMetadata the tool metadata (can be null)
     * @param invocationContext the invocation context (can be null)
     * @throws IllegalArgumentException if executionRequest is null
     */
    public ToolInputGuardrailRequest {
        if (executionRequest == null) {
            throw new IllegalArgumentException("executionRequest cannot be null");
        }
    }

    /**
     * Convenience method to get the tool name from the execution request.
     *
     * @return the tool name
     */
    public String toolName() {
        return executionRequest.name();
    }

    /**
     * Convenience method to get the arguments JSON from the execution request.
     *
     * @return the arguments as JSON string
     */
    public String arguments() {
        return executionRequest.arguments();
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
     * This provides a convenient way to access individual parameters without manually parsing JSON.
     * The JsonObject can map to POJOs using {@code mapTo(Class)}.
     * </p>
     *
     * <p>
     * Example usage:
     * </p>
     *
     * <pre>
     * {@code
     * // Simple parameter access
     * JsonObject args = request.argumentsAsJson();
     * int x = args.getInteger("x");
     * String name = args.getString("name");
     *
     * // Map to POJO
     * record Params(int x, String name) {
     * }
     *
     * Params params = args.mapTo(Params.class);
     * }
     * </pre>
     *
     * @return the arguments as a Vert.x JsonObject
     * @throws io.vertx.core.json.DecodeException if the arguments string is not valid JSON
     * @see JsonObject
     * @see JsonObject#mapTo(Class)
     */
    public JsonObject argumentsAsJson() {
        return new JsonObject(arguments());
    }
}
