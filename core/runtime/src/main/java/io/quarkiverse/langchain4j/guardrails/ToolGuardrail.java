package io.quarkiverse.langchain4j.guardrails;

/**
 * Base interface for all tool guardrails that validate and control tool (function) invocations.
 * <p>
 *
 * <h2>Guardrail Types</h2>
 * <p>
 * There are two types of guardrails, each executed at different stages of tool invocation:
 * </p>
 * <ul>
 * <li><strong>{@link ToolInputGuardrail}</strong> - Executes <em>before</em> the tool, validating
 * parameters and context. Can block execution, modify parameters, or allow execution to proceed.</li>
 * <li><strong>{@link ToolOutputGuardrail}</strong> - Executes <em>after</em> the tool, validating
 * or transforming results. Can filter sensitive data, validate output format, or request LLM to retry.</li>
 * </ul>
 *
 * <h2>Result Types</h2>
 * <p>
 * Guardrails return one of several result types to control execution flow:
 * </p>
 * <ul>
 * <li><strong>success()</strong> - Validation passed, continue with original request/result</li>
 * <li><strong>successWith(...)</strong> - Validation passed with modifications to request/result</li>
 * <li><strong>failure(message)</strong> - Non-fatal validation failure, error returned to LLM</li>
 * <li><strong>failure(message, cause)</strong> - Fatal failure, throws exception and stops execution</li>
 * </ul>
 *
 * <h2>CDI Integration</h2>
 * <p>
 * All guardrail implementations must be CDI beans.
 *
 *
 * <h2>Execution Model</h2>
 * <p>
 * Multiple guardrails can be applied to a single tool and execute in <strong>order</strong>:
 * </p>
 *
 * <pre>{@code
 * @Tool("Send email")
 * @ToolInputGuardrails({
 *         EmailFormatValidator.class, // Executes first
 *         UserAuthorizationGuardrail.class, // Executes second
 *         RateLimitGuardrail.class // Executes third
 * })
 * public String sendEmail(String to, String subject, String body) {
 *     // Implementation
 * }
 * }</pre>
 * <p>
 * Input guardrails use <strong>fail-fast</strong> behavior: execution stops at the first failure.
 * </p>
 *
 * <h2>Event Loop Limitation</h2>
 * <p>
 * <strong>IMPORTANT:</strong> Guardrails have synchronous/blocking APIs and <strong>cannot execute
 * on the Vert.x event loop</strong>. Tools with guardrails must be marked or detected as blocking.
 * Attempting to use guardrails on the event loop will throw {@code ToolExecutionException}.
 * </p>
 *
 * @param <P> the guardrail request type providing context for validation
 * @param <R> the guardrail result type indicating validation outcome
 * @see ToolInputGuardrail
 * @see ToolOutputGuardrail
 * @see ToolInputGuardrails
 * @see ToolOutputGuardrails
 * @see ToolGuardrailRequest
 * @see ToolGuardrailResult
 */
public interface ToolGuardrail<P extends ToolGuardrailRequest, R extends ToolGuardrailResult<R>> {

    /**
     * Validates the tool invocation and returns a result indicating whether to proceed.
     * <p>
     * This method is called synchronously during tool execution and must not block for
     * extended periods. For I/O-bound operations, ensure the tool is marked as blocking.
     * </p>
     *
     * @param request the guardrail request containing tool context and parameters
     * @return the validation result indicating success, failure, or modification
     */
    R validate(P request);
}
