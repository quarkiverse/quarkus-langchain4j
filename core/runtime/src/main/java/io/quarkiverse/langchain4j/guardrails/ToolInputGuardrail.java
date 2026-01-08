package io.quarkiverse.langchain4j.guardrails;

/**
 * Interface for validating tool input parameters before execution.
 * <p>
 * Implementations of this interface are used to validate and potentially modify
 * tool execution requests before the actual tool method is invoked. This allows for:
 * </p>
 * <ul>
 * <li>Input parameter validation (format, range, business rules)</li>
 * <li>Authorization checks (user permissions, rate limiting)</li>
 * <li>Input sanitization or transformation</li>
 * <li>Security validations (injection prevention, data access control)</li>
 * </ul>
 *
 * <p>
 * Example implementation:
 * </p>
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;ApplicationScoped
 *     public class EmailFormatValidator implements ToolInputGuardrail {
 *
 *         private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
 *
 *         @Override
 *         public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
 *             JsonNode args = parseArguments(request.arguments());
 *             String email = args.get("to").asText();
 *
 *             if (!EMAIL_PATTERN.matcher(email).matches()) {
 *                 return ToolInputGuardrailResult.failure(
 *                         "Invalid email format: " + email);
 *             }
 *
 *             return ToolInputGuardrailResult.success();
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>
 * Implementations must either be CDI beans to enable dependency injection, or have a no-args constructor.
 * Common scopes include {@code @ApplicationScoped} for stateless guardrails
 * and {@code @RequestScoped} for guardrails that need per-request state.
 * </p>
 *
 * <p>
 * <strong>Execution Order:</strong> When multiple guardrails are specified via
 * {@link ToolInputGuardrails}, they execute in array order with fail-fast semantics.
 * The first guardrail returning a non-success result stops the chain.
 * </p>
 *
 * @see ToolInputGuardrails
 * @see ToolInputGuardrailRequest
 * @see ToolInputGuardrailResult
 */
public interface ToolInputGuardrail extends ToolGuardrail<ToolInputGuardrailRequest, ToolInputGuardrailResult> {

    /**
     * Validates the tool execution request before the tool is invoked.
     * <p>
     * This method receives complete context about the tool invocation, including:
     * </p>
     * <ul>
     * <li>Tool name and arguments from the LLM</li>
     * <li>Tool metadata (specification, parameters)</li>
     * <li>Invocation context (memory ID, custom parameters)</li>
     * </ul>
     *
     * <p>
     * The implementation should return:
     * </p>
     * <ul>
     * <li>{@link ToolInputGuardrailResult#success()} if validation passes</li>
     * <li>{@link ToolInputGuardrailResult#successWith(dev.langchain4j.agent.tool.ToolExecutionRequest)}
     * to modify the request</li>
     * <li>{@link ToolInputGuardrailResult#failure(String)} for non-fatal validation failures</li>
     * <li>{@link ToolInputGuardrailResult#failure(String, Throwable)} for fatal failures with exceptions</li>
     * </ul>
     *
     * @param request the tool input guardrail request containing all validation context
     * @return the validation result indicating success, failure, or request modification
     */
    ToolInputGuardrailResult validate(ToolInputGuardrailRequest request);
}
