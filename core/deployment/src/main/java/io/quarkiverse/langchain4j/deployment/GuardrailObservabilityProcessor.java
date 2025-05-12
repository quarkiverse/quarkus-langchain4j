package io.quarkiverse.langchain4j.deployment;

import static io.quarkiverse.langchain4j.deployment.GuardrailObservabilityProcessorSupport.*;

import java.util.Optional;
import java.util.function.Consumer;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationTransformation.TransformationContext;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.deployment.GuardrailObservabilityProcessorSupport.TransformType;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.runtime.metrics.MetricsFactory;

/**
 * This processor is responsible for processing observability-related aspects of guardrail execution.
 * Specifically, it automatically instruments input/output guardrail methods
 * with annotations for metrics.
 * <p>
 * The main capabilities include:
 * <ul>
 * <li>Applying Micrometer's `@Timed` and `@Counted` annotations to monitor method execution.</li>
 * <li>Adding OpenTelemetry's `@WithSpan` annotation for distributed tracing.</li>
 * </ul>
 */
public class GuardrailObservabilityProcessor {
    private static final Logger LOG = Logger.getLogger(GuardrailObservabilityProcessor.class);

    @BuildStep
    void transformWithMetrics(Optional<MetricsCapabilityBuildItem> metricsCapability,
            CombinedIndexBuildItem indexBuildItem,
            BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformer) {

        if (metricsCapability.isPresent() && metricsCapability.get().metricsSupported(MetricsFactory.MICROMETER)) {
            LOG.debug("Transforming guardrail methods with @Timed and @Counted annotations");
            annotationsTransformer.produce(
                    new AnnotationsTransformerBuildItem(
                            transformGuardrailValidateMethod(transformationContext -> transformationContext.addAll(
                                    AnnotationInstance.builder(MICROMETER_COUNTED)
                                            .add("value", "guardrail.invoked")
                                            .add("description",
                                                    "Measures the number of times this guardrail was invoked")
                                            .build(),
                                    AnnotationInstance.builder(MICROMETER_TIMED)
                                            .add("value", "guardrail.timed")
                                            .add("description", "Measures the runtime of this guardrail")
                                            .add("percentiles", new double[] { 0.75, 0.95, 0.99 })
                                            .add("histogram", true)
                                            .build()),
                                    indexBuildItem.getIndex(), 2000, TransformType.METRICS)));
        }
    }

    @BuildStep
    void transformWithOtel(Capabilities capabilities,
            CombinedIndexBuildItem indexBuildItem,
            BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformer) {

        if (capabilities.isPresent(Capability.OPENTELEMETRY_TRACER)) {
            LOG.debug("Transforming guardrail methods with @WithSpan annotation");
            annotationsTransformer.produce(
                    new AnnotationsTransformerBuildItem(
                            transformGuardrailValidateMethod(
                                    transformationContext -> transformationContext
                                            .add(AnnotationInstance.builder(WITH_SPAN).build()),
                                    indexBuildItem.getIndex(), TransformType.OTEL)));
        }
    }

    private static AnnotationTransformation transformGuardrailValidateMethod(Consumer<TransformationContext> transformation,
            IndexView index, TransformType transformType) {

        return transformGuardrailValidateMethod(transformation, index,
                AnnotationTransformation.DEFAULT_PRIORITY_VALUE, transformType);
    }

    private static AnnotationTransformation transformGuardrailValidateMethod(Consumer<TransformationContext> transformation,
            IndexView index, int priority, TransformType transformType) {

        return AnnotationTransformation.forMethods()
                // NOTE: The priority is only set because for some reason the @Counted annotation isn't
                // picked up by the interceptor. Once that is fixed we can remove the need
                // for adding @MicrometerCounted
                // See https://github.com/quarkusio/quarkus/pull/47745
                .priority(priority)
                .whenMethod(methodInfo -> shouldTransformMethod(methodInfo, index, transformType))
                .transform(transformation);
    }
}
