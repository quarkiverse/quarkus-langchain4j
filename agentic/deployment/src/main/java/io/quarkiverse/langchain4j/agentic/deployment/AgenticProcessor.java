package io.quarkiverse.langchain4j.agentic.deployment;

import static io.quarkiverse.langchain4j.agentic.deployment.AgenticLangChain4jDotNames.AGENT;
import static io.quarkiverse.langchain4j.agentic.deployment.AgenticLangChain4jDotNames.CONDITIONAL_AGENT;
import static io.quarkiverse.langchain4j.agentic.deployment.AgenticLangChain4jDotNames.LOOP_AGENT;
import static io.quarkiverse.langchain4j.agentic.deployment.AgenticLangChain4jDotNames.PARALLEL_AGENT;
import static io.quarkiverse.langchain4j.agentic.deployment.AgenticLangChain4jDotNames.SEQUENCE_AGENT;
import static io.quarkiverse.langchain4j.agentic.deployment.AgenticLangChain4jDotNames.SUB_AGENT;
import static io.quarkiverse.langchain4j.agentic.deployment.AgenticLangChain4jDotNames.SUPERVISOR_AGENT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

import io.quarkiverse.langchain4j.agentic.runtime.AgenticRecorder;
import io.quarkiverse.langchain4j.agentic.runtime.AiAgentCreateInfo;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public class AgenticProcessor {

    @BuildStep
    void detectAgents(CombinedIndexBuildItem indexBuildItem, BuildProducer<DetectedAgentBuildItem> producer) {
        IndexView index = indexBuildItem.getIndex();

        Map<ClassInfo, List<MethodInfo>> ifaceToAgentMethodsMap = new HashMap<>();
        collectAgentsWithMethodAnnotations(index, AGENT, ifaceToAgentMethodsMap);
        collectAgentsWithMethodAnnotations(index, SUB_AGENT, ifaceToAgentMethodsMap);
        collectAgentsWithMethodAnnotations(index, SUPERVISOR_AGENT, ifaceToAgentMethodsMap);
        collectAgentsWithMethodAnnotations(index, SEQUENCE_AGENT, ifaceToAgentMethodsMap);
        collectAgentsWithMethodAnnotations(index, PARALLEL_AGENT, ifaceToAgentMethodsMap);
        collectAgentsWithMethodAnnotations(index, LOOP_AGENT, ifaceToAgentMethodsMap);
        collectAgentsWithMethodAnnotations(index, CONDITIONAL_AGENT, ifaceToAgentMethodsMap);

        ifaceToAgentMethodsMap.forEach((classInfo, methods) -> {
            producer.produce(new DetectedAgentBuildItem(classInfo, methods));
        });
    }

    @BuildStep
    void cdiSupport(List<DetectedAgentBuildItem> detectedAgentBuildItems, AgenticRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer) {
        for (DetectedAgentBuildItem detectedAgentBuildItem : detectedAgentBuildItems) {
            String chatModelName = NamedConfigUtil.DEFAULT_NAME; // TODO: fix
            syntheticBeanProducer.produce(SyntheticBeanBuildItem
                    .configure(detectedAgentBuildItem.getIface().name())
                    .forceApplicationClass()
                    .createWith(recorder
                            .createAiAgent(new AiAgentCreateInfo(detectedAgentBuildItem.getIface().toString(), chatModelName)))
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .done());
        }

    }

    @BuildStep
    void nativeSupport(List<DetectedAgentBuildItem> detectedAgentBuildItems,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer) {
        String[] agentClassNames = detectedAgentBuildItems.stream().map(bi -> bi.getIface().name().toString())
                .toArray(String[]::new);
        reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(agentClassNames).methods(true).fields(false).build());
    }

    private static void collectAgentsWithMethodAnnotations(IndexView index, DotName annotation,
            Map<ClassInfo, List<MethodInfo>> ifaceToAgentMethodsMap) {
        Collection<AnnotationInstance> annotations = index.getAnnotations(annotation);
        for (AnnotationInstance ai : annotations) {
            if (ai.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }
            MethodInfo methodInfo = ai.target().asMethod();
            ifaceToAgentMethodsMap.computeIfAbsent(methodInfo.declaringClass(), (k) -> new ArrayList<>()).add(methodInfo);
        }
    }

}
