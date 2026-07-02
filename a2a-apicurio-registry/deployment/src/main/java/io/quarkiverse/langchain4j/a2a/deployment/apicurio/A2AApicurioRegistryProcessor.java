package io.quarkiverse.langchain4j.a2a.deployment.apicurio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.a2a.runtime.apicurio.A2AAgentCardPublisher;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.A2AApicurioRegistryRecorder;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.ApicurioAgentsRegistry;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.PublishToAgentRegistry;
import io.quarkiverse.langchain4j.a2a.runtime.apicurio.config.A2AApicurioRegistryBuildTimeConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;

public class A2AApicurioRegistryProcessor {

    private static final Logger log = Logger.getLogger(A2AApicurioRegistryProcessor.class);
    private static final String FEATURE = "langchain4j-a2a-apicurio-registry";

    private static final DotName AGENTS_REGISTRY = DotName.createSimple(ApicurioAgentsRegistry.class);
    private static final DotName A2A_PUBLISHER = DotName.createSimple(A2AAgentCardPublisher.class);
    private static final DotName PUBLISH_ANNOTATION = DotName.createSimple(PublishToAgentRegistry.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerAgentsRegistry(
            A2AApicurioRegistryBuildTimeConfig buildTimeConfig,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            CoreVertxBuildItem vertxBuildItem,
            A2AApicurioRegistryRecorder recorder) {
        if (!buildTimeConfig.enabled()) {
            return;
        }

        log.debug("Apicurio Registry A2A integration enabled, registering ApicurioAgentsRegistry bean");
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(AGENTS_REGISTRY)
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .createWith(recorder.agentsRegistryFunction(vertxBuildItem.getVertx()))
                .done());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerAgentCardPublisher(
            A2AApicurioRegistryBuildTimeConfig buildTimeConfig,
            CombinedIndexBuildItem combinedIndex,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            CoreVertxBuildItem vertxBuildItem,
            A2AApicurioRegistryRecorder recorder) {
        if (!buildTimeConfig.enabled()) {
            return;
        }

        IndexView index = combinedIndex.getIndex();
        Collection<AnnotationInstance> annotations = index.getAnnotations(PUBLISH_ANNOTATION);
        if (annotations.isEmpty()) {
            return;
        }

        AnnotationInstance annotation = annotations.iterator().next();
        String agentName = annotation.value("name").asString();
        String agentDescription = annotation.value("description").asString();
        AnnotationValue versionValue = annotation.value("version");
        String agentVersion = versionValue != null ? versionValue.asString() : "1.0.0";

        List<A2AAgentCardPublisher.SkillInfo> skills = new ArrayList<>();
        AnnotationValue skillsValue = annotation.value("skills");
        if (skillsValue != null) {
            for (AnnotationInstance skillAnnotation : skillsValue.asNestedArray()) {
                skills.add(new A2AAgentCardPublisher.SkillInfo(
                        skillAnnotation.value("id").asString(),
                        skillAnnotation.value("name").asString(),
                        skillAnnotation.value("description").asString()));
            }
        }

        log.debugf("Found @PublishToAgentRegistry on %s, will publish agent card '%s'",
                annotation.target(), agentName);
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(A2A_PUBLISHER)
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .createWith(recorder.agentCardPublisherFunction(
                        vertxBuildItem.getVertx(), agentName, agentDescription, agentVersion, skills))
                .done());
    }
}
