package io.quarkiverse.langchain4j.guardrails;

/**
 * Interface for validating and transforming tool output after execution.
 * <p>
 * Implementations of this interface are used to validate and potentially modify
 * tool execution results before they are returned to the LLM. This allows for:
 * </p>
 * <ul>
 * <li>Output validation (format, content, size constraints)</li>
 * <li>Sensitive data filtering (PII redaction, data masking)</li>
 * <li>Result transformation (truncation, summarization)</li>
 * <li>Compliance checks (data privacy, content policies)</li>
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
 *     public class SensitiveDataFilter implements ToolOutputGuardrail {
 *
 *         private static final Pattern SSN_PATTERN = Pattern.compile("\\d{3}-\\d{2}-\\d{4}");
 *
 *         @Override
 *         public ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request) {
 *             String result = request.resultText();
 *             String filtered = SSN_PATTERN.matcher(result)
 *                     .replaceAll("XXX-XX-XXXX");
 *
 *             if (!filtered.equals(result)) {
 *                 ToolExecutionResult modifiedResult = ToolExecutionResult.builder()
 *                         .resultText(filtered)
 *                         .build();
 *                 return ToolOutputGuardrailResult.successWith(modifiedResult);
 *             }
 *
 *             return ToolOutputGuardrailResult.success();
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
 * {@link ToolOutputGuardrails}, they execute in array order with fail-fast semantics.
 * The first guardrail returning a non-success result stops the chain.
 * </p>
 *
 * @see ToolOutputGuardrails
 * @see ToolOutputGuardrailRequest
 * @see ToolOutputGuardrailResult
 */
public interface ToolOutputGuardrail extends ToolGuardrail<ToolOutputGuardrailRequest, ToolOutputGuardrailResult> {

    /**
     * Validates the tool execution result after the tool has been invoked.
     * <p>
     * This method receives complete context about the tool execution, including:
     * </p>
     * <ul>
     * <li>Tool execution result (text, error status)</li>
     * <li>Original tool execution request</li>
     * <li>Tool metadata (specification, parameters)</li>
     * <li>Invocation context (memory ID, custom parameters)</li>
     * </ul>
     *
     * <p>
     * The implementation should return:
     * </p>
     * <ul>
     * <li>{@link ToolOutputGuardrailResult#success()} if validation passes</li>
     * <li>{@link ToolOutputGuardrailResult#successWith(dev.langchain4j.service.tool.ToolExecutionResult)}
     * to modify the result</li>
     * <li>{@link ToolOutputGuardrailResult#failure(String)} for validation failures</li>
     * <li>{@link ToolOutputGuardrailResult#failure(String, Throwable)} for fatal failures with exceptions</li>
     * </ul>
     *
     * @param request the tool output guardrail request containing all validation context
     * @return the validation result indicating success, failure, or result modification
     */
    ToolOutputGuardrailResult validate(ToolOutputGuardrailRequest request);
}
