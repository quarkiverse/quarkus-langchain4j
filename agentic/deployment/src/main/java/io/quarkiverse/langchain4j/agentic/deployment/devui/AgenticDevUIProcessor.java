package io.quarkiverse.langchain4j.agentic.deployment.devui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import dev.langchain4j.agentic.observability.MonitoredAgent;
import io.quarkiverse.langchain4j.agentic.deployment.AgenticLangChain4jDotNames;
import io.quarkiverse.langchain4j.agentic.deployment.DetectedAiAgentBuildItem;
import io.quarkiverse.langchain4j.agentic.runtime.AgenticRecorder;
import io.quarkiverse.langchain4j.agentic.runtime.devui.AgenticJsonRpcService;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class AgenticDevUIProcessor {

    private static final String INTERNAL_AGENT_PACKAGE_PREFIX = "dev.langchain4j.agentic.";

    private static final List<DotName> CONFIG_ANNOTATIONS = List.of(
            AgenticLangChain4jDotNames.CHAT_MODEL_SUPPLIER,
            AgenticLangChain4jDotNames.CHAT_MEMORY_SUPPLIER,
            AgenticLangChain4jDotNames.CHAT_MEMORY_PROVIDER_SUPPLIER,
            AgenticLangChain4jDotNames.CONTENT_RETRIEVER_SUPPLIER,
            AgenticLangChain4jDotNames.RETRIEVAL_AUGMENTER_SUPPLIER,
            AgenticLangChain4jDotNames.TOOL_SUPPLIER,
            AgenticLangChain4jDotNames.TOOL_PROVIDER_SUPPLIER,
            AgenticLangChain4jDotNames.AGENT_LISTENER_SUPPLIER,
            AgenticLangChain4jDotNames.PARALLEL_EXECUTOR,
            AgenticLangChain4jDotNames.ACTIVATION_CONDITION,
            AgenticLangChain4jDotNames.EXIT_CONDITION,
            AgenticLangChain4jDotNames.ERROR_HANDLER,
            AgenticLangChain4jDotNames.OUTPUT,
            AgenticLangChain4jDotNames.HUMAN_IN_THE_LOOP);

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem cardPage(List<DetectedAiAgentBuildItem> agents) {
        List<DetectedAiAgentBuildItem> userAgents = filterUserAgents(agents);
        Set<String> allSubAgentClassNames = collectAllSubAgentClassNames(userAgents);

        List<AgentInfo> agentInfos = new ArrayList<>();
        for (DetectedAiAgentBuildItem agent : userAgents) {
            agentInfos.add(buildAgentInfo(agent, allSubAgentClassNames));
        }

        CardPageBuildItem card = new CardPageBuildItem();
        card.addBuildTimeData("agents", agentInfos);

        card.addPage(Page.webComponentPageBuilder()
                .title("Agents")
                .componentLink("qwc-agents.js")
                .staticLabel(String.valueOf(userAgents.size()))
                .icon("font-awesome-solid:robot"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Topology")
                .componentLink("qwc-agents-topology.js")
                .icon("font-awesome-solid:diagram-project"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Executions")
                .componentLink("qwc-agents-executions.js")
                .icon("font-awesome-solid:chart-line"));

        card.addPage(Page.webComponentPageBuilder()
                .title("Testing")
                .componentLink("qwc-agents-testing.js")
                .icon("font-awesome-solid:play"));

        return card;
    }

    @BuildStep
    void jsonRpcProvider(BuildProducer<JsonRPCProvidersBuildItem> producers) {
        producers.produce(new JsonRPCProvidersBuildItem(AgenticJsonRpcService.class));
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void markAgentBeansUnremovable(List<DetectedAiAgentBuildItem> agents,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer) {
        for (DetectedAiAgentBuildItem agent : filterUserAgents(agents)) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(agent.getIface().name()));
        }
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void addMonitoredAgentInterface(List<DetectedAiAgentBuildItem> agents,
            BuildProducer<BytecodeTransformerBuildItem> transformers) {
        List<DetectedAiAgentBuildItem> userAgents = filterUserAgents(agents);
        Set<String> allSubAgentClassNames = collectAllSubAgentClassNames(userAgents);

        String monitoredAgentInternal = MonitoredAgent.class.getName().replace('.', '/');

        for (DetectedAiAgentBuildItem agent : userAgents) {
            String className = agent.getIface().name().toString();
            if (allSubAgentClassNames.contains(className) ||
                    agent.getIface().interfaceNames().stream()
                            .anyMatch(dn -> dn.toString().equals(MonitoredAgent.class.getName()))) {
                continue;
            }

            transformers.produce(new BytecodeTransformerBuildItem(className,
                    (name, classVisitor) -> new ClassVisitor(Opcodes.ASM9, classVisitor) {
                        @Override
                        public void visit(int version, int access, String name2, String signature,
                                String superName, String[] interfaces) {
                            String[] extended = Arrays.copyOf(interfaces, interfaces.length + 1);
                            extended[interfaces.length] = monitoredAgentInternal;
                            super.visit(version, access, name2, signature, superName, extended);
                        }
                    }));
        }
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    void enableDevModeMonitoring(List<DetectedAiAgentBuildItem> agents,
            CombinedIndexBuildItem indexBuildItem,
            AgenticRecorder recorder) {
        List<DetectedAiAgentBuildItem> userAgents = filterUserAgents(agents);
        Set<String> allSubAgentClassNames = collectAllSubAgentClassNames(userAgents);
        Set<String> rootAgentClassNames = userAgents.stream()
                .map(a -> a.getIface().name().toString())
                .filter(name -> !allSubAgentClassNames.contains(name))
                .collect(Collectors.toSet());
        recorder.enableDevModeMonitoring(rootAgentClassNames);
        recorder.eagerlyInitRootAgents(rootAgentClassNames);
    }

    private static List<DetectedAiAgentBuildItem> filterUserAgents(List<DetectedAiAgentBuildItem> agents) {
        return agents.stream()
                .filter(a -> !a.getIface().name().toString().startsWith(INTERNAL_AGENT_PACKAGE_PREFIX))
                .toList();
    }

    private Set<String> collectAllSubAgentClassNames(List<DetectedAiAgentBuildItem> agents) {
        Set<String> subAgentClassNames = new HashSet<>();
        for (DetectedAiAgentBuildItem agent : agents) {
            for (MethodInfo method : agent.getAgenticMethods()) {
                for (DotName annotation : AgenticLangChain4jDotNames.AGENT_ANNOTATIONS_WITH_SUB_AGENTS) {
                    AnnotationInstance instance = method.annotation(annotation);
                    if (instance == null) {
                        continue;
                    }
                    AnnotationValue subAgentsValue = instance.value("subAgents");
                    if (subAgentsValue != null) {
                        for (Type subAgentType : subAgentsValue.asClassArray()) {
                            subAgentClassNames.add(subAgentType.name().toString());
                        }
                    }
                    AnnotationValue subAgentValue = instance.value("subAgent");
                    if (subAgentValue != null) {
                        subAgentClassNames.add(subAgentValue.asClass().name().toString());
                    }
                }
            }
        }
        return subAgentClassNames;
    }

    private AgentInfo buildAgentInfo(DetectedAiAgentBuildItem agent, Set<String> allSubAgentClassNames) {
        ClassInfo iface = agent.getIface();
        String className = iface.name().toString();
        String simpleName = iface.simpleName();

        String agentType = "Agent";
        String description = "";
        String outputKey = "";
        List<String> subAgents = new ArrayList<>();

        for (MethodInfo method : agent.getAgenticMethods()) {
            for (DotName annotationName : AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS) {
                AnnotationInstance instance = method.annotation(annotationName);
                if (instance == null) {
                    continue;
                }

                agentType = annotationName.withoutPackagePrefix();

                AnnotationValue descValue = instance.value("description");
                if (descValue != null) {
                    description = descValue.asString();
                }
                AnnotationValue nameValue = instance.value("name");
                if (nameValue != null && !nameValue.asString().isEmpty()) {
                    description = nameValue.asString() + (description.isEmpty() ? "" : " - " + description);
                }

                AnnotationValue outputKeyValue = instance.value("outputKey");
                if (outputKeyValue != null) {
                    outputKey = outputKeyValue.asString();
                }

                AnnotationValue subAgentsValue = instance.value("subAgents");
                if (subAgentsValue != null) {
                    for (Type subType : subAgentsValue.asClassArray()) {
                        subAgents.add(subType.name().toString());
                    }
                }
                AnnotationValue subAgentValue = instance.value("subAgent");
                if (subAgentValue != null) {
                    subAgents.add(subAgentValue.asClass().name().toString());
                }

                // First matching agent annotation wins (an agent method has exactly one)
                break;
            }
        }

        List<String> methods = agent.getAgenticMethods().stream()
                .map(this::formatMethodSignature)
                .toList();

        List<String> configAnnotations = new ArrayList<>();
        for (DotName configAnnotation : CONFIG_ANNOTATIONS) {
            if (!iface.annotations(configAnnotation).isEmpty()) {
                configAnnotations.add("@" + configAnnotation.withoutPackagePrefix());
            }
        }

        boolean isRoot = !allSubAgentClassNames.contains(className);

        return new AgentInfo(className, simpleName, agentType, description, outputKey,
                subAgents, methods, configAnnotations, isRoot);
    }

    private String formatMethodSignature(MethodInfo method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.returnType().name().withoutPackagePrefix());
        sb.append(" ").append(method.name()).append("(");
        List<MethodParameterInfo> params = method.parameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            MethodParameterInfo param = params.get(i);
            sb.append(param.type().name().withoutPackagePrefix());
            if (param.name() != null) {
                sb.append(" ").append(param.name());
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
