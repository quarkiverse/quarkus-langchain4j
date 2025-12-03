package io.quarkiverse.langchain4j.deployment;

import static io.quarkiverse.langchain4j.deployment.GuardrailObservabilityProcessorSupport.MICROMETER_COUNTED;
import static io.quarkiverse.langchain4j.deployment.GuardrailObservabilityProcessorSupport.MICROMETER_TIMED;
import static io.quarkiverse.langchain4j.deployment.GuardrailObservabilityProcessorSupport.TransformType;
import static io.quarkiverse.langchain4j.deployment.GuardrailObservabilityProcessorSupport.WITH_SPAN;
import static io.quarkiverse.langchain4j.deployment.GuardrailObservabilityProcessorSupport.shouldTransformMethod;

import java.util.Optional;
import java.util.function.Consumer;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationTransformation.TransformationContext;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import dev.langchain4j.observability.api.event.InputGuardrailExecutedEvent;
import dev.langchain4j.observability.api.event.OutputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.runtime.observability.ToolInputGuardrailExecutedEvent;
import io.quarkiverse.langchain4j.runtime.observability.ToolOutputGuardrailExecutedEvent;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.runtime.metrics.MetricsFactory;

/**
 * This processor is responsible for processing observability-related aspects of guardrail execution.
 * Specifically, it automatically instruments input/output guardrail methods
 * with annotations for metrics.
 * <p>
 * The main capabilities include:
 * <ul>
 * <li>Registering the guardrail metric observer that collect metrics about guardrail execution</li>
 * <li>Applying Micrometer's `@Timed` and `@Counted` annotations to monitor method execution. (deprecated)</li>
 * <li>Adding OpenTelemetry's `@WithSpan` annotation for distributed tracing.</li>
 * </ul>
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class GuardrailObservabilityProcessor {
    private static final Logger LOG = Logger.getLogger(GuardrailObservabilityProcessor.class);
    public static final String GUARDRAIL_METRICS_OBSERVER_SUPPORT_CLASS = "io.quarkiverse.langchain4j.runtime.observability.GuardrailMetricsObserverSupport";

    /**
     * Metrics collection must only be enabled when Micrometer is available.
     * In practice, however, Arc always registers observers, regardless of whether
     * Micrometer is on the classpath or whether an observer bean is actually
     * registered. As a result, an observer may attempt to record metrics when
     * Micrometer is missing, which can trigger runtime errors and even break
     * native image compilation.
     * <p>
     * To prevent such failures, this method conditionally generates a bean when
     * Micrometer support is detected.
     * The generation logic depends on: {@code io.quarkiverse.langchain4j.runtime.observability.GuardrailMetricsObserverSupport}
     * *
     * </p>
     *
     * @param metricsCapability the metrics capability build item
     * @param generatedBean the generated bean build item producer
     */
    @BuildStep
    void addMetricObserver(
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            BuildProducer<GeneratedBeanBuildItem> generatedBean) {

        if (metricsCapability.isPresent() && metricsCapability.get().metricsSupported(MetricsFactory.MICROMETER)) {
            LOG.debug("Generating GuardrailMetricsObserver bean for guardrail metrics collection");
            ClassOutput output = new GeneratedBeanGizmoAdaptor(generatedBean);
            ClassCreator.Builder classCreatorBuilder = ClassCreator.builder()
                    .classOutput(output)
                    .className("io.quarkiverse.langchain4j.runtime.observability.GuardrailMetricsObserver");
            try (ClassCreator classCreator = classCreatorBuilder.build()) {
                classCreator.addAnnotation(Singleton.class);
                MethodCreator onInputGuardrailExecuted = classCreator.getMethodCreator("onInputGuardrailExecuted", "V",
                        InputGuardrailExecutedEvent.class);
                onInputGuardrailExecuted.getParameterAnnotations(0).addAnnotation(Observes.class);
                var inputGuardrailObserverMethod = MethodDescriptor.ofMethod(
                        GUARDRAIL_METRICS_OBSERVER_SUPPORT_CLASS,
                        "onInputGuardrailExecuted", "V", InputGuardrailExecutedEvent.class);
                onInputGuardrailExecuted.invokeStaticMethod(inputGuardrailObserverMethod,
                        onInputGuardrailExecuted.getMethodParam(0));
                onInputGuardrailExecuted.returnVoid();

                MethodCreator onOutputGuardrailExecuted = classCreator.getMethodCreator("onOutputGuardrailExecuted", "V",
                        OutputGuardrailExecutedEvent.class);
                onOutputGuardrailExecuted.getParameterAnnotations(0).addAnnotation(Observes.class);
                var outputGuardrailObserverMethod = MethodDescriptor.ofMethod(
                        GUARDRAIL_METRICS_OBSERVER_SUPPORT_CLASS,
                        "onOutputGuardrailExecuted", "V", OutputGuardrailExecutedEvent.class);
                onOutputGuardrailExecuted.invokeStaticMethod(outputGuardrailObserverMethod,
                        onOutputGuardrailExecuted.getMethodParam(0));
                onOutputGuardrailExecuted.returnVoid();

                MethodCreator onToolInputGuardrailExecuted = classCreator.getMethodCreator("onToolInputGuardrailExecuted", "V",
                        ToolInputGuardrailExecutedEvent.class);
                onToolInputGuardrailExecuted.getParameterAnnotations(0).addAnnotation(Observes.class);
                var inputToolGuardrailObserverMethod = MethodDescriptor.ofMethod(
                        GUARDRAIL_METRICS_OBSERVER_SUPPORT_CLASS,
                        "onToolInputGuardrailExecuted", "V", ToolInputGuardrailExecutedEvent.class);
                onToolInputGuardrailExecuted.invokeStaticMethod(inputToolGuardrailObserverMethod,
                        onToolInputGuardrailExecuted.getMethodParam(0));
                onToolInputGuardrailExecuted.returnVoid();

                MethodCreator onToolOutputGuardrailExecuted = classCreator.getMethodCreator("onToolOutputGuardrailExecuted",
                        "V",
                        ToolOutputGuardrailExecutedEvent.class);
                onToolOutputGuardrailExecuted.getParameterAnnotations(0).addAnnotation(Observes.class);
                var outputToolGuardrailObserverMethod = MethodDescriptor.ofMethod(
                        GUARDRAIL_METRICS_OBSERVER_SUPPORT_CLASS,
                        "onToolOutputGuardrailExecuted", "V", ToolOutputGuardrailExecutedEvent.class);
                onToolOutputGuardrailExecuted.invokeStaticMethod(outputToolGuardrailObserverMethod,
                        onToolOutputGuardrailExecuted.getMethodParam(0));
                onToolOutputGuardrailExecuted.returnVoid();
            }
        }

    }

    /**
     * @deprecated These metrics are now collected via the GuardrailMetricsObserver bean.
     */
    @Deprecated
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
                                                    "Measures the number of times this guardrail was invoked (deprecated)")
                                            .build(),
                                    AnnotationInstance.builder(MICROMETER_TIMED)
                                            .add("value", "guardrail.timed")
                                            .add("description", "Measures the runtime of this guardrail (deprecated)")
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
