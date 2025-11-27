package io.quarkiverse.langchain4j.guardrails;

/**
 * Base sealed interface for tool guardrail request objects containing validation context.
 * <p>
 * Request objects encapsulate all information needed by guardrails to perform validation,
 * including tool metadata, invocation context, and either input parameters or output results
 * depending on the guardrail type.
 * </p>
 *
 * <h2>Request Types</h2>
 * <p>
 * This sealed interface permits exactly two implementations, corresponding to the two
 * stages of tool invocation where guardrails can execute:
 * </p>
 * <ul>
 * <li><strong>{@link ToolInputGuardrailRequest}</strong> - Provides access to tool input
 * parameters before execution. Contains the tool execution request with arguments, tool
 * metadata, and invocation context. Used by input guardrails to validate parameters,
 * check authorization, or modify the request before the tool executes.</li>
 * <li><strong>{@link ToolOutputGuardrailRequest}</strong> - Provides access to tool output
 * results after execution. Contains the execution result, original request, tool metadata,
 * and invocation context. Used by output guardrails to filter sensitive data, validate
 * output format, or transform results before returning to the LLM.</li>
 * </ul>
 *
 * <h2>Available Context</h2>
 * <p>
 * Both request types provide rich contextual information for validation:
 * </p>
 * <ul>
 * <li><strong>Tool Information</strong>: Tool name, description, parameter schemas</li>
 * <li><strong>Invocation Context</strong>: Memory ID, custom parameters, conversation state</li>
 * <li><strong>Arguments</strong>: Raw JSON string and parsed {@code JsonObject} via {@code argumentsAsJson()}</li>
 * <li><strong>Metadata</strong>: Tool specifications, declarative metadata, runtime information</li>
 * </ul>
 *
 * <h2>Parameter Access</h2>
 * <p>
 * Request objects provide convenient methods for accessing tool parameters:
 * </p>
 *
 * <pre>{@code
 * // Raw JSON string access
 * String rawArgs = request.executionRequest().arguments();
 *
 * // Parsed JsonObject for easy access
 * JsonObject args = request.argumentsAsJson();
 * String email = args.getString("email");
 * int maxResults = args.getInteger("maxResults", 10);
 *
 * // POJO mapping for complex parameters
 * MyParams params = args.mapTo(MyParams.class);
 * }</pre>
 *
 * <h2>Immutability</h2>
 * <p>
 * Request objects are immutable value objects. Modifications to tool execution must be
 * returned via result objects ({@link ToolGuardrailResult}) using {@code successWith()}.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * Request objects are thread-safe and can be safely accessed from multiple threads.
 * However, guardrail execution itself is always single-threaded per tool invocation.
 * </p>
 *
 * @param <P> the concrete request type (self-bounded generic for type safety)
 * @see ToolInputGuardrailRequest
 * @see ToolOutputGuardrailRequest
 * @see ToolGuardrail
 * @see ToolGuardrailResult
 * @see ToolInvocationContext
 * @see ToolMetadata
 */
public sealed interface ToolGuardrailRequest<P extends ToolGuardrailRequest<P>>
        permits ToolInputGuardrailRequest, ToolOutputGuardrailRequest {

}