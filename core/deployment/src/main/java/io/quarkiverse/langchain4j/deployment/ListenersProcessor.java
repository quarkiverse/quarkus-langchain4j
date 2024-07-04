package io.quarkiverse.langchain4j.deployment;

import java.util.Optional;

import io.quarkiverse.langchain4j.runtime.listeners.MetricsChatModelListener;
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;

public class ListenersProcessor {

    @BuildStep
    public void spanListeners(Capabilities capabilities,
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {
        var addOpenTelemetrySpan = capabilities.isPresent(Capability.OPENTELEMETRY_TRACER);
        if (addOpenTelemetrySpan) {
            additionalBeanProducer.produce(
                    AdditionalBeanBuildItem.builder().addBeanClass(SpanChatModelListener.class).setUnremovable().build());
        }

        var addMicrometerMetrics = metricsCapability.isPresent()
                && metricsCapability.get().metricsSupported(MetricsFactory.MICROMETER);
        if (addMicrometerMetrics) {
            additionalBeanProducer.produce(
                    AdditionalBeanBuildItem.builder().addBeanClass(MetricsChatModelListener.class).setUnremovable().build());
        }
    }
}
