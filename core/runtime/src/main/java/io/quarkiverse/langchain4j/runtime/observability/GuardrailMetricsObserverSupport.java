package io.quarkiverse.langchain4j.runtime.observability;

import java.util.concurrent.TimeUnit;

import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

/**
 * Support class to observes guardrail execution events and records metrics with detailed tags.
 * <p>
 * This observer listens to {@link InputGuardrailExecutedEvent} and {@link OutputGuardrailExecutedEvent}
 * and records both counter and timer metrics with the following tags:
 * <ul>
 * <li><strong>aiservice</strong>: The fully qualified name of the AI service interface</li>
 * <li><strong>operation</strong>: The AI service method name</li>
 * <li><strong>guardrail</strong>: The simple class name of the guardrail</li>
 * <li><strong>guardrail.type</strong>: The type of guardrail ({@link GuardrailType})</li>
 * <li><strong>outcome</strong>: The result of the guardrail execution ({@link GuardrailOutcome})</li>
 * </ul>
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
 * This class is only providing static methods, so simplify the generation logic.
 * </p>
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
