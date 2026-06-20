package io.quarkiverse.langchain4j.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.RAG_PIPELINE;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.REGISTER_AI_SERVICES;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.RETRIEVAL_AUGMENTOR;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.VOID_CLASS;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.runtime.aiservice.ComponentResolutionMode;
import io.quarkiverse.langchain4j.runtime.aiservice.DeclarativeAiServiceCreateInfo.ComponentEntry;
import io.quarkiverse.langchain4j.runtime.rag.RagPipelineCreateInfo;
import io.quarkiverse.langchain4j.runtime.rag.RagPipelineRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public class RagPipelineProcessor {

    private static final Logger log = Logger.getLogger(RagPipelineProcessor.class);

    /**
     * Scans for {@code @RagPipeline} annotations, validates them, and produces build items.
     * Companion pipelines produce {@link RagPipelineBuildItem}; standalone pipelines produce
     * {@link StandaloneRagPipelineBuildItem} for the RUNTIME_INIT step.
     * <p>
     * Kept separate from synthetic bean creation so that the RUNTIME_INIT phase is only entered
     * when standalone pipelines actually exist.
     */
    @BuildStep
    void scanAndValidateRagPipelines(
            CombinedIndexBuildItem combinedIndex,
            List<DeclarativeAiServiceBuildItem> aiServices,
            BuildProducer<RagPipelineBuildItem> ragPipelineProducer,
            BuildProducer<StandaloneRagPipelineBuildItem> standaloneProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer) {

        IndexView index = combinedIndex.getIndex();

        Set<String> aiServiceClassNames = aiServices.stream()
                .map(bi -> bi.getServiceClassInfo().name().toString())
                .collect(Collectors.toSet());

        for (AnnotationInstance annotation : index.getAnnotations(RAG_PIPELINE)) {
            // Defensive guard: @RagPipeline targets TYPE only, but guard against future widening
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            ClassInfo classInfo = annotation.target().asClass();
            String className = classInfo.name().toString();

            // Validation 1: must be an interface
            if (!Modifier.isInterface(classInfo.flags())) {
                throw new IllegalStateException("@RagPipeline must be applied to an interface");
            }

            // Parse annotation attributes
            ComponentEntry augmentor = resolveComponent(annotation.value("augmentor"));
            List<String> retrieverClassNames = resolveRetrievers(annotation.value("retrievers"));
            ComponentEntry router = resolveComponent(annotation.value("router"));
            ComponentEntry transformer = resolveComponent(annotation.value("transformer"));
            ComponentEntry aggregator = resolveComponent(annotation.value("aggregator"));
            ComponentEntry injector = resolveComponent(annotation.value("injector"));

            // Validation 2: pre-built augmentor cannot be combined with decomposed attributes
            if (augmentor.mode() == ComponentResolutionMode.EXPLICIT) {
                boolean hasDecomposed = !retrieverClassNames.isEmpty()
                        || router.mode() != ComponentResolutionMode.SKIP
                        || transformer.mode() != ComponentResolutionMode.SKIP
                        || aggregator.mode() != ComponentResolutionMode.SKIP
                        || injector.mode() != ComponentResolutionMode.SKIP;
                if (hasDecomposed) {
                    throw new IllegalStateException(
                            "Pre-built augmentor mode cannot be combined with decomposed pipeline attributes on "
                                    + className);
                }
            }

            // Validation 3: at least one retriever or a router must be specified (unless pre-built)
            if (augmentor.mode() == ComponentResolutionMode.SKIP
                    && retrieverClassNames.isEmpty()
                    && router.mode() == ComponentResolutionMode.SKIP) {
                throw new IllegalStateException(
                        "At least one retriever or a router must be specified on " + className);
            }

            // Validation 4: router + retrievers is an error
            if (router.mode() == ComponentResolutionMode.EXPLICIT && !retrieverClassNames.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot specify both router and retrievers on " + className
                                + " — router defines its own retrieval strategy");
            }

            RagPipelineCreateInfo createInfo = new RagPipelineCreateInfo(
                    augmentor, retrieverClassNames, router, transformer, aggregator, injector);

            // Register all explicit component classes for reflection and as unremovable beans
            registerComponents(createInfo, reflectiveClassProducer, unremovableBeanProducer);

            // Detect mode: companion (also an AI service) or standalone
            boolean isCompanion = classInfo.hasAnnotation(REGISTER_AI_SERVICES)
                    || aiServiceClassNames.contains(className);

            if (isCompanion) {
                log.debugf("@RagPipeline on %s detected as companion mode (AI service)", className);
                ragPipelineProducer.produce(new RagPipelineBuildItem(className, createInfo));
            } else {
                log.debugf("@RagPipeline on %s detected as standalone mode", className);
                standaloneProducer.produce(new StandaloneRagPipelineBuildItem(classInfo.name(), createInfo));
            }
        }
    }

    /**
     * Creates synthetic {@link dev.langchain4j.rag.RetrievalAugmentor} CDI beans for
     * standalone {@code @RagPipeline} interfaces. Runs at RUNTIME_INIT only when at
     * least one standalone pipeline exists.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void createStandaloneRagPipelines(
            List<StandaloneRagPipelineBuildItem> standalonePipelines,
            RagPipelineRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer) {

        for (StandaloneRagPipelineBuildItem pipeline : standalonePipelines) {
            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(RETRIEVAL_AUGMENTOR)
                    .addType(ClassType.create(pipeline.getInterfaceDotName()))
                    .createWith(recorder.createStandaloneRagPipeline(pipeline.getCreateInfo()))
                    .setRuntimeInit()
                    .unremovable()
                    .scope(ApplicationScoped.class);

            addRagInjectionPoints(configurator, pipeline.getCreateInfo());

            syntheticBeanProducer.produce(configurator.done());
        }
    }

    private ComponentEntry resolveComponent(AnnotationValue annotationValue) {
        if (annotationValue == null) {
            return ComponentEntry.SKIP;
        }
        DotName dotName = annotationValue.asClass().name();
        if (VOID_CLASS.equals(dotName)) {
            return ComponentEntry.SKIP;
        }
        return new ComponentEntry(dotName.toString(), ComponentResolutionMode.EXPLICIT);
    }

    private List<String> resolveRetrievers(AnnotationValue annotationValue) {
        if (annotationValue == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (org.jboss.jandex.Type type : annotationValue.asClassArray()) {
            DotName dotName = type.name();
            if (!VOID_CLASS.equals(dotName)) {
                result.add(dotName.toString());
            }
        }
        return List.copyOf(result);
    }

    private void registerComponents(RagPipelineCreateInfo info,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer) {
        registerIfExplicit(info.augmentor(), reflectiveClassProducer, unremovableBeanProducer);
        for (String retriever : info.retrieverClassNames()) {
            registerClass(retriever, reflectiveClassProducer, unremovableBeanProducer);
        }
        registerIfExplicit(info.router(), reflectiveClassProducer, unremovableBeanProducer);
        registerIfExplicit(info.transformer(), reflectiveClassProducer, unremovableBeanProducer);
        registerIfExplicit(info.aggregator(), reflectiveClassProducer, unremovableBeanProducer);
        registerIfExplicit(info.injector(), reflectiveClassProducer, unremovableBeanProducer);
    }

    private void registerIfExplicit(ComponentEntry entry,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer) {
        if (entry.mode() == ComponentResolutionMode.EXPLICIT) {
            registerClass(entry.className(), reflectiveClassProducer, unremovableBeanProducer);
        }
    }

    private void registerClass(String className,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer) {
        reflectiveClassProducer.produce(
                ReflectiveClassBuildItem.builder(className).constructors(true).build());
        unremovableBeanProducer.produce(
                UnremovableBeanBuildItem.beanTypes(DotName.createSimple(className)));
    }

    /**
     * Adds injection points to a synthetic bean configurator that match exactly what
     * {@link io.quarkiverse.langchain4j.runtime.rag.RagPipelineSupport#buildAugmentor} resolves
     * via {@code ctx.getInjectedReference()}.
     * <p>
     * Package-private so that {@code AiServicesProcessor} can call it for companion mode.
     */
    static void addRagInjectionPoints(
            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator,
            RagPipelineCreateInfo info) {

        if (info.augmentor().mode() == ComponentResolutionMode.EXPLICIT) {
            configurator.addInjectionPoint(
                    ClassType.create(DotName.createSimple(info.augmentor().className())));
            return; // pre-built mode — augmentor manages its own executor
        }

        for (String retriever : info.retrieverClassNames()) {
            configurator.addInjectionPoint(
                    ClassType.create(DotName.createSimple(retriever)));
        }
        if (info.router().mode() == ComponentResolutionMode.EXPLICIT) {
            configurator.addInjectionPoint(
                    ClassType.create(DotName.createSimple(info.router().className())));
        }
        if (info.transformer().mode() == ComponentResolutionMode.EXPLICIT) {
            configurator.addInjectionPoint(
                    ClassType.create(DotName.createSimple(info.transformer().className())));
        }
        if (info.aggregator().mode() == ComponentResolutionMode.EXPLICIT) {
            configurator.addInjectionPoint(
                    ClassType.create(DotName.createSimple(info.aggregator().className())));
        }
        if (info.injector().mode() == ComponentResolutionMode.EXPLICIT) {
            configurator.addInjectionPoint(
                    ClassType.create(DotName.createSimple(info.injector().className())));
        }

        // Decomposed mode always needs a ManagedExecutor for parallel retrieval
        configurator.addInjectionPoint(
                ClassType.create(DotName.createSimple("org.eclipse.microprofile.context.ManagedExecutor")));
    }
}
