package io.quarkiverse.langchain4j.runtime.observability;

import java.util.concurrent.TimeUnit;

import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

/**
 * Support class that observes guardrail execution events and records metrics with detailed tags.
 * <p>
 * This class provides static methods for observing both AI service-level guardrails and tool-level guardrails:
 * </p>
 * <ul>
 * <li><strong>AI Service Guardrails</strong>: Listens to {@link InputGuardrailExecutedEvent} and
 * {@link OutputGuardrailExecutedEvent}, recording metrics as {@code guardrail.invoked} and {@code guardrail.timed}</li>
 * <li><strong>Tool Guardrails</strong>: Listens to {@link ToolInputGuardrailExecutedEvent} and
 * {@link ToolOutputGuardrailExecutedEvent}, recording metrics as {@code tool-guardrail.invoked} and
 * {@code tool-guardrail.timed}</li>
 * </ul>
 *
 * <h2>AI Service Guardrail Metrics Tags</h2>
 * <ul>
 * <li><strong>aiservice</strong>: The fully qualified name of the AI service interface</li>
 * <li><strong>operation</strong>: The AI service method name</li>
 * <li><strong>guardrail</strong>: The class name of the guardrail</li>
 * <li><strong>guardrail.type</strong>: The type of guardrail ("input" or "output")</li>
 * <li><strong>outcome</strong>: The result of the guardrail execution ("success", "failure", or "reprompt")</li>
 * </ul>
 *
 * <h2>Tool Guardrail Metrics Tags</h2>
 * <ul>
 * <li><strong>aiservice</strong>: The fully qualified name of the AI service interface</li>
 * <li><strong>operation</strong>: The AI service method name</li>
 * <li><strong>tool.name</strong>: The name of the tool being validated</li>
 * <li><strong>guardrail</strong>: The class name of the guardrail</li>
 * <li><strong>guardrail.type</strong>: The type of guardrail ("input" or "output")</li>
 * <li><strong>outcome</strong>: The result of the guardrail execution ("success", "failure", or "fatal")</li>
 * </ul>
 *
 * <p>
 * Metrics collection must only be enabled when Micrometer is available.
 * In practice, however, Arc always registers observers, regardless of whether
 * Micrometer is on the classpath or whether an observer bean is actually
 * registered. As a result, an observer may attempt to record metrics when
 * Micrometer is missing, which can trigger runtime errors and even break
 * native image compilation.
 * </p>
 *
 * <p>
 * To prevent such failures, this class is only referenced by a bean that is
 * conditionally generated when Micrometer support is detected.
 * This class only provides static methods to simplify the generation logic.
 * </p>
 *
 * @see ToolInputGuardrailExecutedEvent
 * @see ToolOutputGuardrailExecutedEvent
 * @see GuardrailType
 * @see ToolGuardrailOutcome
 */
public class GuardrailMetricsObserverSupport {

    /**
     * Observes input guardrail execution events and records metrics.
     *
     * @param event the input guardrail executed event
     */
    public static void onInputGuardrailExecuted(InputGuardrailExecutedEvent event) {
        String aiServiceName = event.invocationContext().interfaceName();
        String methodName = event.invocationContext().methodName();
        String guardrailName = sanitize(event.guardrailClass().getName());
        GuardrailOutcome outcome = determineInputGuardrailOutcome(event);
        long durationNanos = event.duration().toNanos();

        recordMetrics(GuardrailType.INPUT, aiServiceName, methodName, guardrailName, outcome, durationNanos);
    }

    /**
     * Observes output guardrail execution events and records metrics.
     *
     * @param event the output guardrail executed event
     */
    public static void onOutputGuardrailExecuted(OutputGuardrailExecutedEvent event) {
        String aiServiceName = event.invocationContext().interfaceName();
        String methodName = event.invocationContext().methodName();
        String guardrailName = sanitize(event.guardrailClass().getName());
        GuardrailOutcome outcome = determineOutputGuardrailOutcome(event);
        long durationNanos = event.duration().toNanos();

        recordMetrics(GuardrailType.OUTPUT, aiServiceName, methodName, guardrailName, outcome, durationNanos);
    }

    /**
     * Observes tool input guardrail execution events and records metrics.
     *
     * @param event the tool input guardrail executed event
     */
    public static void onToolInputGuardrailExecuted(ToolInputGuardrailExecutedEvent event) {
        String aiServiceName = event.toolInvocationContext().context().interfaceName();
        String methodName = event.toolInvocationContext().context().methodName();
        String toolName = event.toolName();
        String guardrailName = sanitize(event.toolClass().getName());
        ToolGuardrailOutcome outcome = event.outcome();
        long durationNanos = event.duration();

        recordToolMetrics(GuardrailType.INPUT, aiServiceName, methodName, toolName, guardrailName, outcome,
                durationNanos);
    }

    /**
     * Observes tool output guardrail execution events and records metrics.
     *
     * @param event the tool output guardrail executed event
     */
    public static void onToolOutputGuardrailExecuted(ToolOutputGuardrailExecutedEvent event) {
        String aiServiceName = event.toolInvocationContext().context().interfaceName();
        String methodName = event.toolInvocationContext().context().methodName();
        String toolName = event.toolName();
        String guardrailName = sanitize(event.toolClass().getName());
        ToolGuardrailOutcome outcome = event.outcome();
        long durationNanos = event.duration();

        recordToolMetrics(GuardrailType.OUTPUT, aiServiceName, methodName, toolName, guardrailName, outcome,
                durationNanos);
    }

    private static String sanitize(String simpleName) {
        if (simpleName == null) {
            return null;
        }
        if (simpleName.endsWith("_ClientProxy")) {
            return simpleName.substring(0, simpleName.length() - "_ClientProxy".length()).replace("_", "$");
        }
        return simpleName;
    }

    private static void recordMetrics(GuardrailType guardrailType, String aiServiceName, String methodName,
            String guardrailName, GuardrailOutcome outcome, long durationNanos) {

        Counter.builder("guardrail.invoked")
                .description("Number of guardrail invocations")
                .tags(
                        "aiservice", aiServiceName,
                        "operation", methodName,
                        "guardrail", guardrailName,
                        "guardrail.type", guardrailType.getValue(),
                        "outcome", outcome.getValue())
                .register(Metrics.globalRegistry)
                .increment();

        Timer.builder("guardrail.timed")
                .description("Guardrail execution time")
                .tags("aiservice", aiServiceName,
                        "operation", methodName,
                        "guardrail", guardrailName,
                        "guardrail.type", guardrailType.getValue(),
                        "outcome", outcome.getValue())
                .publishPercentiles(new double[] { 0.75, 0.95, 0.99 })
                .publishPercentileHistogram(true)
                .register(Metrics.globalRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private static void recordToolMetrics(GuardrailType guardrailType, String aiServiceName, String methodName,
            String toolName, String guardrailName, ToolGuardrailOutcome outcome, long durationNanos) {

        Counter.builder("tool-guardrail.invoked")
                .description("Number of tool guardrail invocations")
                .tags(
                        "aiservice", aiServiceName,
                        "operation", methodName,
                        "tool.name", toolName,
                        "guardrail", guardrailName,
                        "guardrail.type", guardrailType.getValue(),
                        "outcome", outcome.getValue())
                .register(Metrics.globalRegistry)
                .increment();

        Timer.builder("tool-guardrail.timed")
                .description("Tool guardrail execution time")
                .tags("aiservice", aiServiceName,
                        "operation", methodName,
                        "tool.name", toolName,
                        "guardrail", guardrailName,
                        "guardrail.type", guardrailType.getValue(),
                        "outcome", outcome.getValue())
                .publishPercentiles(new double[] { 0.75, 0.95, 0.99 })
                .publishPercentileHistogram(true)
                .register(Metrics.globalRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private static GuardrailOutcome determineInputGuardrailOutcome(InputGuardrailExecutedEvent event) {
        // Check if the guardrail execution was successful
        if (event.result().isSuccess()) {
            return GuardrailOutcome.SUCCESS;
        } else {
            return GuardrailOutcome.FAILURE;
        }
    }

    private static GuardrailOutcome determineOutputGuardrailOutcome(OutputGuardrailExecutedEvent event) {
        // Check the result type to determine the outcome
        if (event.result().isSuccess()) {
            return GuardrailOutcome.SUCCESS;
        } else if (event.result().isReprompt()) {
            return GuardrailOutcome.REPROMPT;
        } else {
            return GuardrailOutcome.FAILURE;
        }
    }
}
