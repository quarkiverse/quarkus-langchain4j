package io.quarkiverse.langchain4j.guardrails;

/**
 * Base sealed interface for tool guardrail validation results.
 * <p>
 * Result objects communicate the outcome of guardrail validation and control the execution
 * flow of tool invocations. Guardrails return result objects to indicate whether validation
 * succeeded, failed, or requires modifications to the request or response.
 * </p>
 *
 * <h2>Result Types</h2>
 * <p>
 * This sealed interface permits exactly two implementations, corresponding to the two
 * guardrail types:
 * </p>
 * <ul>
 * <li><strong>{@link ToolInputGuardrailResult}</strong> - Returned by input guardrails to
 * control whether tool execution proceeds. Can allow execution with original or modified
 * parameters, or block execution with an error message.</li>
 * <li><strong>{@link ToolOutputGuardrailResult}</strong> - Returned by output guardrails to
 * control what result is returned to the LLM. Can return original or modified output,
 * return an error, or request the LLM to retry with guidance (reprompt).</li>
 * </ul>
 *
 * <h2>Result Outcomes</h2>
 * <p>
 * Guardrails can return several types of results to control execution flow:
 * </p>
 * <ul>
 * <li><strong>Success</strong> - Validation passed, continue with original request/result:
 *
 * <pre>{@code
 * return ToolInputGuardrailResult.success();
 * return ToolOutputGuardrailResult.success();
 * }</pre>
 *
 * </li>
 * <li><strong>Success with Modification</strong> - Validation passed, but request/result modified:
 *
 * <pre>{@code
 * // Modify input parameters
 * ToolExecutionRequest modified = ToolExecutionRequest.builder()
 *         .id(request.executionRequest().id())
 *         .name(request.executionRequest().name())
 *         .arguments(normalizedArgs)
 *         .build();
 * return ToolInputGuardrailResult.successWith(modified);
 *
 * // Filter output data
 * ToolExecutionResult filtered = ToolExecutionResult.builder()
 *         .resultText(filteredOutput)
 *         .build();
 * return ToolOutputGuardrailResult.successWith(filtered);
 * }</pre>
 *
 * </li>
 * <li><strong>Non-Fatal Failure</strong> - Validation failed, error returned to LLM:
 *
 * <pre>{@code
 * // Tool will not execute, error message returned to LLM
 * return ToolInputGuardrailResult.failure("Invalid email format");
 *
 * // Original output discarded, error message returned to LLM
 * return ToolOutputGuardrailResult.failure("Output too large");
 * }</pre>
 *
 * </li>
 * <li><strong>Fatal Failure</strong> - Critical error, throws exception:
 *
 * <pre>{@code
 * // Execution stops completely, exception thrown
 * return ToolInputGuardrailResult.failure(
 *         "Security violation", new SecurityException("Unauthorized"));
 * }</pre>
 *
 * </li>
 * </ul>
 *
 * <h2>Execution Flow Control</h2>
 * <p>
 * Result objects control the execution flow of tool invocations:
 * </p>
 * <ul>
 * <li><strong>Input Guardrails</strong>:
 * <ul>
 * <li>Success → Tool executes with (possibly modified) parameters</li>
 * <li>Failure → Tool does NOT execute, error returned to LLM</li>
 * <li>Fatal Failure → Exception thrown, execution stops</li>
 * </ul>
 * </li>
 * <li><strong>Output Guardrails</strong>:
 * <ul>
 * <li>Success → LLM receives (possibly modified) result</li>
 * <li>Failure → LLM receives error message instead of result</li>
 * <li>Fatal Failure → Exception thrown, execution stops</li>
 * </ul>
 * </li>
 * </ul>
 *
 * <h2>Chained Guardrails</h2>
 * <p>
 * When multiple guardrails are applied to a tool, they execute in <strong>array order</strong>
 * with <strong>fail-fast</strong> behavior for input guardrails:
 * </p>
 *
 * <pre>{@code
 * &#64;ToolInputGuardrails({
 *     FormatValidator.class,    // Executes first
 *     AuthorizationCheck.class, // Executes second (only if first succeeds)
 *     RateLimiter.class         // Executes third (only if first two succeed)
 * })
 * }</pre>
 * <p>
 * If any guardrail returns a failure result, subsequent guardrails in the chain are
 * <strong>not executed</strong>, and the tool does not run.
 * </p>
 *
 * <h2>Immutability</h2>
 * <p>
 * Result objects are immutable value objects created through static factory methods.
 * Once created, their state cannot be modified.
 * </p>
 *
 * @param <GR> the concrete result type (self-bounded generic for type safety)
 * @see ToolInputGuardrailResult
 * @see ToolOutputGuardrailResult
 * @see ToolGuardrail
 * @see ToolGuardrailRequest
 */
public sealed interface ToolGuardrailResult<GR extends ToolGuardrailResult<GR>>
        permits ToolInputGuardrailResult, ToolOutputGuardrailResult {

}