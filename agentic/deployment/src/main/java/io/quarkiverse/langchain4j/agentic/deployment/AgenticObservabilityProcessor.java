package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.quarkiverse.langchain4j.agentic.runtime.AgenticRecorder;
import io.quarkiverse.langchain4j.agentic.runtime.observability.AgentCdiEventListener;
import io.quarkiverse.langchain4j.agentic.runtime.observability.AgentHealthCheck;
import io.quarkiverse.langchain4j.agentic.runtime.observability.AgentMetricsListener;
import io.quarkiverse.langchain4j.agentic.runtime.observability.AgentSpanListener;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;
import io.quarkus.smallrye.health.deployment.spi.HealthBuildItem;

public class AgenticObservabilityProcessor {

    @BuildStep
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    void registerObservabilityListeners(
            Capabilities capabilities,
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {

        // OTel spans — conditional on OpenTelemetry tracer
        if (capabilities.isPresent(Capability.OPENTELEMETRY_TRACER)) {
            additionalBeanProducer.produce(
                    AdditionalBeanBuildItem.builder()
                            .addBeanClass(AgentSpanListener.class)
                            .setUnremovable()
                            .build());
        }

        // Micrometer metrics — conditional on Micrometer
        if (metricsCapability.isPresent()
                && metricsCapability.get().metricsSupported(MetricsFactory.MICROMETER)) {
            additionalBeanProducer.produce(
                    AdditionalBeanBuildItem.builder()
                            .addBeanClass(AgentMetricsListener.class)
                            .setUnremovable()
                            .build());
        }

        // CDI events — unconditional
        additionalBeanProducer.produce(
                AdditionalBeanBuildItem.builder()
                        .addBeanClass(AgentCdiEventListener.class)
                        .setUnremovable()
                        .build());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerHealthCheck(
            Capabilities capabilities,
            List<DetectedAiAgentBuildItem> detectedAgents,
            AgenticRecorder recorder,
            BuildProducer<HealthBuildItem> healthBuildItems) {

        if (capabilities.isPresent(Capability.SMALLRYE_HEALTH)) {
            Set<String> agentClassNames = new HashSet<>();
            for (DetectedAiAgentBuildItem agent : detectedAgents) {
                agentClassNames.add(agent.getIface().name().toString());
            }
            recorder.setHealthCheckAgentClassNames(agentClassNames);
            healthBuildItems.produce(new HealthBuildItem(AgentHealthCheck.class.getName(), true));
        }
    }
}
