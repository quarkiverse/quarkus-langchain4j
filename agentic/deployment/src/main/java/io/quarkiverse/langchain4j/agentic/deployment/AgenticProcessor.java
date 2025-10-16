package io.quarkiverse.langchain4j.agentic.deployment;

import static io.quarkiverse.langchain4j.agentic.deployment.ValidationUtil.validateAllowedReturnTypes;
import static io.quarkiverse.langchain4j.agentic.deployment.ValidationUtil.validateNoMethodParameters;
import static io.quarkiverse.langchain4j.agentic.deployment.ValidationUtil.validateRequiredParameterTypes;
import static io.quarkiverse.langchain4j.agentic.deployment.ValidationUtil.validateStaticMethod;

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
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import dev.langchain4j.service.IllegalConfigurationException;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.runtime.AgenticRecorder;
import io.quarkiverse.langchain4j.agentic.runtime.AiAgentCreateInfo;
import io.quarkiverse.langchain4j.deployment.AnnotationsImpliesAiServiceBuildItem;
import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.FallbackToDummyUserMessageBuildItem;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkiverse.langchain4j.deployment.PreventToolValidationErrorBuildItem;
import io.quarkiverse.langchain4j.deployment.RequestChatModelBeanBuildItem;
import io.quarkiverse.langchain4j.deployment.SkipOutputFormatInstructionsBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public class AgenticProcessor {

    private static final Logger log = Logger.getLogger(AgenticProcessor.class);

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-agentic"));
    }

    @BuildStep
    void detectAgents(CombinedIndexBuildItem indexBuildItem, BuildProducer<DetectedAiAgentBuildItem> producer) {
        IndexView index = indexBuildItem.getIndex();

        Map<ClassInfo, List<MethodInfo>> ifaceToAgentMethodsMap = new HashMap<>();
        for (DotName dotName : AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS) {
            collectAgentsWithMethodAnnotations(index, dotName, ifaceToAgentMethodsMap);
        }

        ifaceToAgentMethodsMap.forEach((classInfo, methods) -> {
            Optional<MethodInfo> chatModelSupplier = classInfo.methods().stream()
                    .filter(m -> Modifier.isStatic(m.flags()) && m.hasAnnotation(
                            AgenticLangChain4jDotNames.CHAT_MODEL_SUPPLIER))
                    .findFirst();

            List<MethodInfo> mcpToolBoxMethods = methods.stream()
                    .filter(mi -> mi.hasAnnotation(LangChain4jDotNames.MCP_TOOLBOX)).toList();
            DetectedAiAgentBuildItem item = new DetectedAiAgentBuildItem(classInfo, methods, chatModelSupplier.orElse(null),
                    mcpToolBoxMethods);
            validate(item);
            producer.produce(
                    item);
        });
    }

    private void validate(DetectedAiAgentBuildItem item) {
        ClassInfo iface = item.getIface();
        validateActivationCondition(iface);
        validateBeforeAgentInvocation(iface);
        validateAfterAgentInvocation(iface);
        validateChatMemoryProviderSupplier(iface);
        validateChatMemorySupplier(iface);
        validateChatModelSupplier(iface);
        validateContentRetrieverSupplier(iface);
        validateErrorHandler(iface);
        validateExitCondition(iface);
        validateHumanInTheLoop(iface);
        validateHumanInTheLoopResponseSupplier(iface);
        validateOutput(iface);
        validateParallelExecutor(iface);
        validateRetrievalAugmentorSupplier(iface);
        validateToolProviderSupplier(iface);
        validateToolSupplier(iface);
    }

    private static void validateActivationCondition(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.ACTIVATION_CONDITION;
        List<AnnotationInstance> instances = iface.annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(DotNames.PRIMITIVE_BOOLEAN),
                    annotationToValidate);
        }
    }

    private void validateBeforeAgentInvocation(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.BEFORE_AGENT_INVOCATION;
        List<AnnotationInstance> instances = iface.annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateRequiredParameterTypes(method, List.of(AgenticLangChain4jDotNames.AGENT_REQUEST),
                    annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(DotNames.VOID),
                    annotationToValidate);
        }
    }

    private void validateAfterAgentInvocation(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.AFTER_AGENT_INVOCATION;
        List<AnnotationInstance> instances = iface.annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateRequiredParameterTypes(method, List.of(AgenticLangChain4jDotNames.AGENT_RESPONSE), annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(DotNames.VOID), annotationToValidate);
        }
    }

    private void validateChatMemoryProviderSupplier(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.CHAT_MEMORY_PROVIDER_SUPPLIER;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateRequiredParameterTypes(method, List.of(DotNames.OBJECT), annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(LangChain4jDotNames.CHAT_MEMORY), annotationToValidate);
        }
    }

    private void validateChatMemorySupplier(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.CHAT_MEMORY_SUPPLIER;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateNoMethodParameters(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(LangChain4jDotNames.CHAT_MEMORY), annotationToValidate);
        }
    }

    private void validateChatModelSupplier(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.CHAT_MODEL_SUPPLIER;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateNoMethodParameters(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(LangChain4jDotNames.CHAT_MODEL), annotationToValidate);
        }
    }

    private void validateContentRetrieverSupplier(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.CONTENT_RETRIEVER_SUPPLIER;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateNoMethodParameters(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(LangChain4jDotNames.RETRIEVER), annotationToValidate);
        }
    }

    private void validateErrorHandler(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.ERROR_HANDLER;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateRequiredParameterTypes(method, List.of(AgenticLangChain4jDotNames.ERROR_CONTEXT), annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(AgenticLangChain4jDotNames.ERROR_RECOVERY_RESULT), annotationToValidate);
        }
    }

    private void validateExitCondition(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.EXIT_CONDITION;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(DotNames.PRIMITIVE_BOOLEAN), annotationToValidate);
        }
    }

    private void validateHumanInTheLoop(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.HUMAN_IN_THE_LOOP;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(DotNames.VOID), annotationToValidate);
        }
    }

    private void validateHumanInTheLoopResponseSupplier(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.HUMAN_IN_THE_LOOP_RESPONSE_SUPPLIER;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateNoMethodParameters(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(DotNames.STRING), annotationToValidate);
        }
    }

    private void validateOutput(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.OUTPUT;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
        }
    }

    private void validateParallelExecutor(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.PARALLEL_EXECUTOR;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateNoMethodParameters(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(DotNames.EXECUTOR), annotationToValidate);
        }
    }

    private void validateRetrievalAugmentorSupplier(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.RETRIEVAL_AUGMENTER_SUPPLIER;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateNoMethodParameters(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(LangChain4jDotNames.RETRIEVAL_AUGMENTOR), annotationToValidate);
        }
    }

    private void validateToolProviderSupplier(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.TOOL_PROVIDER_SUPPLIER;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateNoMethodParameters(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(LangChain4jDotNames.TOOL_PROVIDER), annotationToValidate);
        }
    }

    private void validateToolSupplier(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.TOOL_SUPPLIER;
        List<AnnotationInstance> instances = iface
                .annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateNoMethodParameters(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(DotNames.OBJECT, DotNames.OBJECT_ARRAY), annotationToValidate);
        }
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
    AnnotationsImpliesAiServiceBuildItem implyAiService() {
        return new AnnotationsImpliesAiServiceBuildItem(AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS);
    }

    @BuildStep
    SkipOutputFormatInstructionsBuildItem skipOutputInstructions() {
        Set<DotName> skippedReturnTypes = Set.of(AgenticLangChain4jDotNames.AGENTIC_SCOPE,
                AgenticLangChain4jDotNames.RESULT_WITH_AGENTIC_SCOPE, DotName.OBJECT_NAME,
                // this one is just a hack....
                io.quarkus.arc.processor.DotNames.LIST);
        return new SkipOutputFormatInstructionsBuildItem(new Predicate<>() {
            @Override
            public boolean test(MethodInfo methodInfo) {
                return skippedReturnTypes.contains(methodInfo.returnType().name());
            }
        });
    }

    @BuildStep
    FallbackToDummyUserMessageBuildItem fallbackToDummyUserMessage() {
        return new FallbackToDummyUserMessageBuildItem(new Predicate<>() {
            @Override
            public boolean test(MethodInfo methodInfo) {
                for (DotName dotName : AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS) {
                    if (methodInfo.hasAnnotation(dotName)) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void mcpToolBoxSupport(List<DetectedAiAgentBuildItem> detectedAgentBuildItems, AgenticRecorder recorder) {
        Set<String> agentsWithMcpToolBox = new HashSet<>();
        for (DetectedAiAgentBuildItem bi : detectedAgentBuildItems) {
            if (!bi.getMcpToolBoxMethods().isEmpty()) {
                if ((bi.getMcpToolBoxMethods().size() != 1) && (bi.getAgenticMethods().size() > 1)) {
                    throw new IllegalConfigurationException(
                            "Currently, @McpToolBox can only be used on an Agent if the agent has a single method. This restriction will be lifted in the future. Offending class is '"
                                    + bi.getIface().name() + "'");
                }
                agentsWithMcpToolBox.add(bi.getIface().name().toString());
            }
        }
        recorder.setAgentsWithMcpToolBox(agentsWithMcpToolBox);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void cdiSupport(List<DetectedAiAgentBuildItem> detectedAiAgentBuildItems, AgenticRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            BuildProducer<RequestChatModelBeanBuildItem> requestChatModelBeanProducer) {

        Set<String> requestedChatModelNames = new HashSet<>();
        for (DetectedAiAgentBuildItem detectedAiAgentBuildItem : detectedAiAgentBuildItems) {
            String chatModelName = NamedConfigUtil.DEFAULT_NAME; // TODO: we need to fix this and provide a way to let the user pick the name of the chat model
            requestedChatModelNames.add(chatModelName);

            AiAgentCreateInfo.ChatModelInfo chatModelInfo = detectedAiAgentBuildItem.getChatModelSupplier() != null
                    ? new AiAgentCreateInfo.ChatModelInfo.FromAnnotation()
                    : new AiAgentCreateInfo.ChatModelInfo.FromBeanWithName(chatModelName);

            SyntheticBeanBuildItem.ExtendedBeanConfigurator beanConfigurator = SyntheticBeanBuildItem
                    .configure(detectedAiAgentBuildItem.getIface().name())
                    .forceApplicationClass()
                    .createWith(recorder
                            .createAiAgent(
                                    new AiAgentCreateInfo(detectedAiAgentBuildItem.getIface().toString(), chatModelInfo)))
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
            beanConfigurator.addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                    new Type[] { ClassType.create(LangChain4jDotNames.TOOL_PROVIDER) }, null));
            syntheticBeanProducer.produce(beanConfigurator.done());
        }
        requestedChatModelNames.forEach(name -> requestChatModelBeanProducer.produce(new RequestChatModelBeanBuildItem(name)));
    }

    @BuildStep
    void nativeSupport(List<DetectedAiAgentBuildItem> detectedAiAgentBuildItems,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxyProducer) {
        String[] agentClassNames = detectedAiAgentBuildItems.stream().map(bi -> bi.getIface().name().toString())
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
            if (!methodInfo.declaringClass().isInterface()) {
                // we need to skio non-AI agents (https://docs.langchain4j.dev/tutorials/agents/#non-ai-agents)
                continue;
            }
            ClassInfo iface = methodInfo.declaringClass();
            addMethodToMap(methodInfo, iface, ifaceToAgentMethodsMap);
            index.getAllKnownSubinterfaces(iface.name()).forEach(i -> addMethodToMap(methodInfo, i, ifaceToAgentMethodsMap));
        }
    }

    private static void addMethodToMap(MethodInfo methodInfo, ClassInfo iface, Map<ClassInfo, List<MethodInfo>> map) {
        map.computeIfAbsent(iface, (k) -> new ArrayList<>()).add(methodInfo);
    }

}
