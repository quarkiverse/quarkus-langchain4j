package io.quarkiverse.langchain4j.agentic.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.runtime.AgenticRecorder;
import io.quarkiverse.langchain4j.agentic.runtime.AiAgentCreateInfo;
import io.quarkiverse.langchain4j.deployment.PreventToolValidationErrorBuildItem;
import io.quarkiverse.langchain4j.deployment.RequestChatModelBeanBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public class AgenticProcessor {

    @BuildStep
    void detectAgents(CombinedIndexBuildItem indexBuildItem, BuildProducer<DetectedAgentBuildItem> producer) {
        IndexView index = indexBuildItem.getIndex();

        Map<ClassInfo, List<MethodInfo>> ifaceToAgentMethodsMap = new HashMap<>();
        for (DotName dotName : AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS) {
            collectAgentsWithMethodAnnotations(index, dotName, ifaceToAgentMethodsMap);
        }

        ifaceToAgentMethodsMap.forEach((classInfo, methods) -> {
            // TODO: introduce validation
            Optional<MethodInfo> chatModelSupplier = classInfo.methods().stream()
                    .filter(m -> Modifier.isStatic(m.flags()) && m.hasAnnotation(
                            AgenticLangChain4jDotNames.CHAT_MODEL_SUPPLIER))
                    .findFirst();
            producer.produce(new DetectedAgentBuildItem(classInfo, methods, chatModelSupplier.orElse(null)));
        });
    }

    @BuildStep
    PreventToolValidationErrorBuildItem supportTools() {
        return new PreventToolValidationErrorBuildItem(new Predicate<>() {
            @Override
            public boolean test(ClassInfo classInfo) {
                for (DotName dotName : AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS) {
                    if (classInfo.hasAnnotation(dotName)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void cdiSupport(List<DetectedAgentBuildItem> detectedAgentBuildItems, AgenticRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            BuildProducer<RequestChatModelBeanBuildItem> requestChatModelBeanProducer) {

        Set<String> requestedChatModelNames = new HashSet<>();
        for (DetectedAgentBuildItem detectedAgentBuildItem : detectedAgentBuildItems) {
            String chatModelName = NamedConfigUtil.DEFAULT_NAME; // TODO: we need to fix this and provide a way to let the user pick the name of the chat model
            requestedChatModelNames.add(chatModelName);

            AiAgentCreateInfo.ChatModelInfo chatModelInfo = detectedAgentBuildItem.getChatModelSupplier() != null
                    ? new AiAgentCreateInfo.ChatModelInfo.FromAnnotation()
                    : new AiAgentCreateInfo.ChatModelInfo.FromBeanWithName(chatModelName);
            SyntheticBeanBuildItem.ExtendedBeanConfigurator beanConfigurator = SyntheticBeanBuildItem
                    .configure(detectedAgentBuildItem.getIface().name())
                    .forceApplicationClass()
                    .createWith(recorder
                            .createAiAgent(new AiAgentCreateInfo(detectedAgentBuildItem.getIface().toString(), chatModelInfo)))
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class);
            if (chatModelInfo instanceof AiAgentCreateInfo.ChatModelInfo.FromBeanWithName f) {
                AnnotationInstance qualifier;
                if (NamedConfigUtil.isDefault(f.name())) {
                    qualifier = AnnotationInstance.builder(Default.class).build();
                } else {
                    qualifier = AnnotationInstance.builder(ModelName.class).add("value", f.name()).build();
                }
                beanConfigurator.addInjectionPoint(
                        ClassType.create(DotName.createSimple(dev.langchain4j.model.chat.ChatModel.class)), qualifier);
            }
            syntheticBeanProducer.produce(beanConfigurator.done());
        }
        requestedChatModelNames.forEach(name -> requestChatModelBeanProducer.produce(new RequestChatModelBeanBuildItem(name)));
    }

    @BuildStep
    void nativeSupport(List<DetectedAgentBuildItem> detectedAgentBuildItems,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxyProducer) {
        String[] agentClassNames = detectedAgentBuildItems.stream().map(bi -> bi.getIface().name().toString())
                .toArray(String[]::new);
        reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(agentClassNames).methods(true).fields(false).build());
        proxyProducer.produce(new NativeImageProxyDefinitionBuildItem(agentClassNames));
    }

    private static void collectAgentsWithMethodAnnotations(IndexView index, DotName annotation,
            Map<ClassInfo, List<MethodInfo>> ifaceToAgentMethodsMap) {
        Collection<AnnotationInstance> annotations = index.getAnnotations(annotation);
        for (AnnotationInstance ai : annotations) {
            if (ai.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }
            MethodInfo methodInfo = ai.target().asMethod();
            ClassInfo iface = methodInfo.declaringClass();
            addMethodToMap(methodInfo, iface, ifaceToAgentMethodsMap);
            index.getAllKnownSubinterfaces(iface.name()).forEach(i -> addMethodToMap(methodInfo, i, ifaceToAgentMethodsMap));
        }
    }

    private static void addMethodToMap(MethodInfo methodInfo, ClassInfo iface, Map<ClassInfo, List<MethodInfo>> map) {
        map.computeIfAbsent(iface, (k) -> new ArrayList<>()).add(methodInfo);
    }

}
