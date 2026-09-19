package io.quarkiverse.langchain4j.deployment;

import java.util.Optional;

import io.quarkiverse.langchain4j.runtime.conversation.ConversationThreadContextProvider;
import io.quarkiverse.langchain4j.runtime.listeners.ConversationIdSpanContributor;
import io.quarkiverse.langchain4j.runtime.listeners.MetricsChatModelListener;
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
import io.quarkiverse.langchain4j.runtime.tool.ConversationIdToolSpanContributor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;
import io.quarkus.smallrye.context.deployment.spi.ThreadContextProviderBuildItem;

public class ListenersProcessor {

    private static final String CONVERSATION_ID_SPAN_CONTRIBUTOR = ConversationIdSpanContributor.class.getName();
    private static final String CONVERSATION_ID_TOOL_SPAN_CONTRIBUTOR = ConversationIdToolSpanContributor.class.getName();

    @BuildStep
    public void spanListeners(Capabilities capabilities,
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<ExcludedTypeBuildItem> excludedTypeProducer) {
        var addOpenTelemetrySpan = capabilities.isPresent(Capability.OPENTELEMETRY_TRACER);
        if (addOpenTelemetrySpan) {
            additionalBeanProducer.produce(
                    AdditionalBeanBuildItem.builder().addBeanClass(SpanChatModelListener.class).setUnremovable().build());
            additionalBeanProducer.produce(
                    AdditionalBeanBuildItem.builder()
                            .addBeanClass(CONVERSATION_ID_SPAN_CONTRIBUTOR)
                            .setUnremovable().build());
            additionalBeanProducer.produce(
                    AdditionalBeanBuildItem.builder()
                            .addBeanClass(CONVERSATION_ID_TOOL_SPAN_CONTRIBUTOR)
                            .setUnremovable().build());
        } else {
            excludedTypeProducer.produce(new ExcludedTypeBuildItem(CONVERSATION_ID_SPAN_CONTRIBUTOR));
            excludedTypeProducer.produce(new ExcludedTypeBuildItem(CONVERSATION_ID_TOOL_SPAN_CONTRIBUTOR));
        }

        var addMicrometerMetrics = metricsCapability.isPresent()
                && metricsCapability.get().metricsSupported(MetricsFactory.MICROMETER);
        if (addMicrometerMetrics) {
            additionalBeanProducer.produce(
                    AdditionalBeanBuildItem.builder().addBeanClass(MetricsChatModelListener.class).setUnremovable().build());
        }
    }

    @BuildStep
    public void conversationContextPropagation(BuildProducer<ThreadContextProviderBuildItem> producer) {
        producer.produce(new ThreadContextProviderBuildItem(ConversationThreadContextProvider.class));
    }
}
