package io.quarkiverse.langchain4j.runtime.observability;

import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.arc.Unremovable;

/**
 * Observes guardrail execution events and records metrics with detailed tags.
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
 * This observer is only active when Micrometer is available in the application.
 */
@ApplicationScoped
@Unremovable
public class GuardrailMetricsObserver {

    @Inject
    Instance<MeterRegistry> registry;

    /**
     * Observes input guardrail execution events and records metrics.
     *
     * @param event the input guardrail executed event
     */
    public void onInputGuardrailExecuted(@Observes InputGuardrailExecutedEvent event) {
        if (registry.isUnsatisfied()) {
            return;
        }

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
    public void onOutputGuardrailExecuted(@Observes OutputGuardrailExecutedEvent event) {
        if (registry.isUnsatisfied()) {
            return;
        }

        String aiServiceName = event.invocationContext().interfaceName();
        String methodName = event.invocationContext().methodName();
        String guardrailName = sanitize(event.guardrailClass().getName());
        GuardrailOutcome outcome = determineOutputGuardrailOutcome(event);
        long durationNanos = event.duration().toNanos();

        recordMetrics(GuardrailType.OUTPUT, aiServiceName, methodName, guardrailName, outcome, durationNanos);
    }

    private String sanitize(String simpleName) {
        if (simpleName == null) {
            return null;
        }
        if (simpleName.endsWith("_ClientProxy")) {
            return simpleName.substring(0, simpleName.length() - "_ClientProxy".length()).replace("_", "$");
        }
        return simpleName;
    }

    private void recordMetrics(GuardrailType guardrailType, String aiServiceName, String methodName,
            String guardrailName, GuardrailOutcome outcome, long durationNanos) {

        MeterRegistry meterRegistry = registry.get();

        Counter.builder("guardrail.invoked")
                .description("Number of guardrail invocations")
                .tags(
                        "aiservice", aiServiceName,
                        "operation", methodName,
                        "guardrail", guardrailName,
                        "guardrail.type", guardrailType.getValue(),
                        "outcome", outcome.getValue())
                .register(meterRegistry)
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
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private GuardrailOutcome determineInputGuardrailOutcome(InputGuardrailExecutedEvent event) {
        // Check if the guardrail execution was successful
        if (event.result().isSuccess()) {
            return GuardrailOutcome.SUCCESS;
        } else {
            return GuardrailOutcome.FAILURE;
        }
    }

    private GuardrailOutcome determineOutputGuardrailOutcome(OutputGuardrailExecutedEvent event) {
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
