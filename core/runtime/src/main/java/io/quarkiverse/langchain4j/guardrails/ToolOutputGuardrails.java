package io.quarkiverse.langchain4j.guardrails;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies guardrails to be executed after tool execution.
 * <p>
 * Guardrails validate tool output and can transform results before they are returned to the LLM.
 * Multiple guardrails are executed in the order they appear in the array, with fail-fast semantics
 * (the first guardrail that fails stops the execution chain).
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * &#64;Tool("Fetch user data")
 * @ToolOutputGuardrails({
 *         SensitiveDataFilter.class,
 *         OutputSizeLimiter.class
 * })
 * public String getUserData(String userId) {
 *     // Implementation
 * }
 * }
 * </pre>
 *
 * <p>
 * Guardrail implementations must be CDI beans (e.g., {@code @ApplicationScoped} or {@code @RequestScoped})
 * and implement the {@link ToolOutputGuardrail} interface.
 * </p>
 *
 *
 * <p>
 * <strong>Note:</strong> This feature is implemented in Quarkus LangChain4j and may be contributed
 * to the upstream LangChain4j library in the future.
 * </p>
 *
 * @see ToolOutputGuardrail
 * @see ToolInputGuardrails
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolOutputGuardrails {

    /**
     * Array of guardrail classes to execute after tool invocation.
     * <p>
     * Guardrails are executed in the order they appear in the array.
     * The first guardrail that returns a failure result will stop the execution chain.
     * </p>
     *
     * @return array of guardrail classes
     */
    Class<? extends ToolOutputGuardrail>[] value();
}
