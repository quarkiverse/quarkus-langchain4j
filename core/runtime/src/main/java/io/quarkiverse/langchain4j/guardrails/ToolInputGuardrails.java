package io.quarkiverse.langchain4j.guardrails;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies guardrails to be executed before tool execution.
 * <p>
 * Guardrails validate tool input parameters and execution context before the tool method is invoked.
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
 * &#64;Tool("Send an email")
 * @ToolInputGuardrails({
 *         EmailFormatValidator.class,
 *         UserAuthorizationGuardrail.class
 * })
 * public String sendEmail(String to,
 *         String subject,
 *         String body) {
 *     // Implementation
 * }
 * }
 * </pre>
 *
 * <p>
 * Guardrail implementations must be CDI beans (e.g., {@code @ApplicationScoped} or {@code @RequestScoped})
 * and implement the {@link ToolInputGuardrail} interface.
 * </p>
 *
 * <p>
 * <strong>Note:</strong> This feature is implemented in Quarkus LangChain4j and may be contributed
 * to the upstream LangChain4j library in the future.
 * </p>
 *
 * @see ToolInputGuardrail
 * @see ToolOutputGuardrails
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolInputGuardrails {

    /**
     * Array of guardrail classes to execute before tool invocation.
     * <p>
     * Guardrails are executed in the order they appear in the array.
     * The first guardrail that returns a failure result will stop the execution chain
     * and prevent the tool from being invoked.
     * </p>
     *
     * @return array of guardrail classes
     */
    Class<? extends ToolInputGuardrail>[] value();
}
