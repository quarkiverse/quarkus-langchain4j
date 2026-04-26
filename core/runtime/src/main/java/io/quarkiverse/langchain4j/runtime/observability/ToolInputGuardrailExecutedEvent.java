package io.quarkiverse.langchain4j.runtime.observability;

import io.quarkiverse.langchain4j.guardrails.ToolInvocationContext;

/**
 * Event fired when a tool input guardrail completes execution.
 * <p>
 * This event is emitted for every input guardrail execution, regardless of
 * whether it succeeds, fails, or fails fatally. The {@link #outcome()} field
 * indicates the result of the guardrail validation.
 * </p>
 *
 * @param toolInvocationContext the context of the tool invocation
 * @param toolClass the guardrail class that was executed
 * @param toolName the name of the tool whose input was validated
 * @param outcome the outcome of the guardrail execution (SUCCESS, FAILURE, or FATAL)
 * @param duration the execution time in nanoseconds
 * @see ToolOutputGuardrailExecutedEvent
 * @see ToolGuardrailOutcome
 */
public record ToolInputGuardrailExecutedEvent(ToolInvocationContext toolInvocationContext, Class<?> toolClass,
        String toolName,
        ToolGuardrailOutcome outcome,
        long duration) {
}
