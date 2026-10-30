package io.quarkiverse.langchain4j.agentic.deployment;

import static io.quarkiverse.langchain4j.agentic.deployment.ValidationUtil.validateAllowedReturnTypes;
import static io.quarkiverse.langchain4j.agentic.deployment.ValidationUtil.validateNoMethodParameters;
import static io.quarkiverse.langchain4j.agentic.deployment.ValidationUtil.validateRequiredParameterTypes;
import static io.quarkiverse.langchain4j.agentic.deployment.ValidationUtil.validateStaticMethod;
import static io.quarkiverse.langchain4j.deployment.AiServicesProcessor.AGENTIC_PACKAGE_PREFIX;
import static io.quarkiverse.langchain4j.deployment.AiServicesProcessor.TOOLBOX;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.scope.AgenticScopeAccess;
import dev.langchain4j.observability.api.listener.AiServiceResponseReceivedListener;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.agentic.runtime.AbstractQuarkusAgent;
import io.quarkiverse.langchain4j.agentic.runtime.AbstractQuarkusDecisionAgent;
import io.quarkiverse.langchain4j.agentic.runtime.AgentClassCreateInfo;
import io.quarkiverse.langchain4j.agentic.runtime.AgenticRecorder;
import io.quarkiverse.langchain4j.agentic.runtime.AiAgentCreateInfo;
import io.quarkiverse.langchain4j.agentic.runtime.DecisionAgentInfo;
import io.quarkiverse.langchain4j.deployment.AnnotationsImpliesAiServiceBuildItem;
import io.quarkiverse.langchain4j.deployment.DecisionTypes;
import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.ExcludeFromImpliedAiServiceBuildItem;
import io.quarkiverse.langchain4j.deployment.FallbackToDummyUserMessageBuildItem;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkiverse.langchain4j.deployment.PreventToolValidationErrorBuildItem;
import io.quarkiverse.langchain4j.deployment.RequestChatModelBeanBuildItem;
import io.quarkiverse.langchain4j.deployment.RequestDecisionModelBeanBuildItem;
import io.quarkiverse.langchain4j.deployment.RequestImageModelBeanBuildItem;
import io.quarkiverse.langchain4j.deployment.RequestModerationModelBeanBuildItem;
import io.quarkiverse.langchain4j.deployment.SkipOutputFormatInstructionsBuildItem;
import io.quarkiverse.langchain4j.deployment.SkipToolBoxProcessingBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.InterceptorResolverBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BeanResolver;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.gizmo.CatchBlockCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.gizmo.TryBlock;

public class AgenticProcessor {

    private static final Logger log = Logger.getLogger(AgenticProcessor.class);

    private static final MethodDescriptor HANDLER_INVOKE = MethodDescriptor.ofMethod(
            InvocationHandler.class, "invoke", Object.class, Object.class, Method.class, Object[].class);

    /**
     * The annotations of the agents that langchain4j-agentic implements with an AI service built from their interface:
     * only the AI agents, decision agents being excluded separately. Workflows, planners, supervisors, human-in-the-loop
     * and remote agents are implemented otherwise, so their interfaces are not AI services.
     */
    private static final List<DotName> AI_SERVICE_AGENT_ANNOTATIONS = List.of(AgenticLangChain4jDotNames.AGENT);

    /**
     * The annotations of the agents that use the chat model of their agentic system: the AI agents, and the supervisors,
     * whose planner is an LLM. The other agents only forward it to the sub-agents that langchain4j-agentic creates itself,
     * while Quarkus provides them as beans, with their own chat model.
     */
    private static final Set<DotName> CHAT_MODEL_AGENT_ANNOTATIONS = Set.of(AgenticLangChain4jDotNames.AGENT,
            AgenticLangChain4jDotNames.SUPERVISOR_AGENT);

    /**
     * The annotations of the static methods that langchain4j-agentic invokes, and whose parameters can be resolved
     * from CDI: the helper methods, and the static agent methods of non-AI and human-in-the-loop agents.
     */
    private static final List<DotName> CDI_RESOLVABLE_METHOD_ANNOTATIONS;

    static {
        List<DotName> annotations = new ArrayList<>(AgenticLangChain4jDotNames.ALL_HELPER_ANNOTATIONS);
        annotations.add(AgenticLangChain4jDotNames.AGENT);
        annotations.add(AgenticLangChain4jDotNames.HUMAN_IN_THE_LOOP);
        CDI_RESOLVABLE_METHOD_ANNOTATIONS = List.copyOf(annotations);
    }

    private static final DotName INTERCEPTOR_BINDING = DotName.createSimple(jakarta.interceptor.InterceptorBinding.class);

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-agentic"));
    }

    @BuildStep
    void detectAgents(CombinedIndexBuildItem indexBuildItem,
            BuildProducer<DetectedAiAgentAsMapBuildItem> aiMapProducer,
            BuildProducer<DetectedNonAiAgentAsMapBuildItem> nonAiMapProducer,
            BuildProducer<DetectedAiAgentBuildItem> producer) {
        IndexView index = indexBuildItem.getIndex();

        Map<ClassInfo, List<MethodInfo>> ifaceToAgentMethodsMap = new HashMap<>();
        Map<ClassInfo, List<MethodInfo>> classToNonAiAgentMethodsMap = new HashMap<>();
        for (DotName dotName : AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS) {
            collectAgentsWithMethodAnnotations(index, dotName, ifaceToAgentMethodsMap, classToNonAiAgentMethodsMap);
        }
        aiMapProducer.produce(DetectedAiAgentAsMapBuildItem.from(ifaceToAgentMethodsMap));
        nonAiMapProducer.produce(DetectedNonAiAgentAsMapBuildItem.from(classToNonAiAgentMethodsMap));

        ifaceToAgentMethodsMap.forEach((classInfo, methods) -> {
            if (classInfo.name().toString().startsWith(AGENTIC_PACKAGE_PREFIX)) {
                return;
            }
            Optional<MethodInfo> chatModelSupplier = classInfo.methods().stream()
                    .filter(m -> Modifier.isStatic(m.flags()) && m.hasAnnotation(
                            AgenticLangChain4jDotNames.CHAT_MODEL_SUPPLIER))
                    .findFirst();

            String modelName = extractModelName(methods);

            if (chatModelSupplier.isPresent() && modelName != null) {
                throw new IllegalConfigurationException(
                        "Agent interface '" + classInfo.name()
                                + "' cannot use both @ChatModelSupplier and @ModelName. "
                                + "Use one or the other to specify the ChatModel.");
            }

            List<MethodInfo> mcpToolBoxMethods = methods.stream()
                    .filter(mi -> mi.hasAnnotation(LangChain4jDotNames.MCP_TOOLBOX)).toList();
            List<MethodInfo> toolBoxMethods = methods.stream()
                    .filter(mi -> mi.hasAnnotation(TOOLBOX)).toList();
            List<MethodInfo> skillsMethods = methods.stream()
                    .filter(mi -> mi.hasAnnotation(AgenticLangChain4jDotNames.SKILLS)).toList();
            DetectedAiAgentBuildItem item = new DetectedAiAgentBuildItem(classInfo, methods, chatModelSupplier.orElse(null),
                    modelName, mcpToolBoxMethods, toolBoxMethods, skillsMethods,
                    detectDecisionAgent(classInfo, methods, index));
            validate(item, index);
            producer.produce(
                    item);
        });
    }

    /**
     * Returns the facts about the given agent interface when it is a decision agent, an interface whose {@code @Agent}
     * method is annotated with {@code @Decide}, or that declares a {@code @DecisionModelSupplier} method, or whose
     * {@code @Agent} method returns an object with fields annotated with {@code @Decide}, like a record. Upstream only
     * applies the last rule together with one of the others, so these agents are built by
     * {@code AgenticRecorder.createDecisionAgent}.
     */
    private static DetectedAiAgentBuildItem.DecisionAgent detectDecisionAgent(ClassInfo iface, List<MethodInfo> methods,
            IndexView index) {
        MethodInfo agentMethod = methods.stream()
                .filter(m -> m.hasDeclaredAnnotation(AgenticLangChain4jDotNames.AGENT))
                .findFirst().orElse(null);
        if (agentMethod == null) {
            return null;
        }
        // like upstream, the suppliers are looked up among the methods of the interface itself
        MethodInfo modelSupplier = declaredMethodWith(iface, AgenticLangChain4jDotNames.DECISION_MODEL_SUPPLIER);
        MethodInfo thresholdSupplier = declaredMethodWith(iface, AgenticLangChain4jDotNames.DECISION_THRESHOLD_SUPPLIER);
        Type contentType = DecisionTypes.contentType(agentMethod.returnType());
        if (!agentMethod.hasDeclaredAnnotation(DecisionTypes.DECIDE) && modelSupplier == null
                && DecisionTypes.objectAnswerType(contentType, index) == null) {
            return null;
        }
        String agentName = explicitAgentName(agentMethod.declaredAnnotation(AgenticLangChain4jDotNames.AGENT));
        return new DetectedAiAgentBuildItem.DecisionAgent(agentMethod,
                agentName != null ? agentName : agentMethod.name(), modelSupplier, thresholdSupplier,
                DecisionTypes.hasBooleanAnswer(contentType, index));
    }

    private static MethodInfo declaredMethodWith(ClassInfo iface, DotName annotation) {
        return iface.methods().stream().filter(m -> m.hasDeclaredAnnotation(annotation)).findFirst().orElse(null);
    }

    private void validate(DetectedAiAgentBuildItem item, IndexView index) {
        ClassInfo iface = item.getIface();
        validateDecisionAgent(item, index);
        validateA2AServerUrlSupplier(iface, index);
        validateActivationCondition(iface);
        validateAgentListenerSupplier(iface);
        validateBeforeCall(iface, index);
        validateChatMemoryProviderSupplier(iface, index);
        validateChatMemorySupplier(iface);
        validateChatModelSupplier(iface);
        validateContentRetrieverSupplier(iface);
        validateErrorHandler(iface, index);
        validateExitCondition(iface);
        validateHumanInTheLoop(iface);
        validateMcpClientSupplier(iface);
        validateMcpToolBox(item);
        validateOutput(iface);
        validateParallelExecutor(iface, index);
        validateRetrievalAugmentorSupplier(iface);
        validateToolProviderSupplier(iface);
        validateToolSupplier(iface);
    }

    private void validateDecisionAgent(DetectedAiAgentBuildItem item, IndexView index) {
        ClassInfo iface = item.getIface();
        DetectedAiAgentBuildItem.DecisionAgent decisionAgent = item.getDecisionAgent();
        if (decisionAgent == null) {
            for (DotName supplier : List.of(AgenticLangChain4jDotNames.DECISION_MODEL_SUPPLIER,
                    AgenticLangChain4jDotNames.DECISION_THRESHOLD_SUPPLIER)) {
                if (!iface.annotations(supplier).isEmpty()) {
                    throw new IllegalConfigurationException(("Agent interface '%s' declares a method annotated with @%s, "
                            + "but it is not a decision agent: a decision agent is an interface whose @Agent method is "
                            + "annotated with @Decide or returns an object with @Decide fields")
                            .formatted(iface.name(), supplier.withoutPackagePrefix()));
                }
            }
            return;
        }

        MethodInfo agentMethod = decisionAgent.agentMethod();
        for (DotName prompt : List.of(LangChain4jDotNames.USER_MESSAGE, LangChain4jDotNames.SYSTEM_MESSAGE)) {
            if (agentMethod.hasDeclaredAnnotation(prompt) || iface.hasDeclaredAnnotation(prompt)) {
                throw new IllegalConfigurationException(("Decision agent '%s' must not use @%s: a decision agent has no "
                        + "prompt, its question is the one of @Decide").formatted(iface.name(), prompt.withoutPackagePrefix()));
            }
        }
        if (item.getModelName() != null && decisionAgent.decisionModelSupplier() != null) {
            throw new IllegalConfigurationException(("Decision agent '%s' cannot use both @DecisionModelSupplier and "
                    + "@ModelName. Use one or the other to specify the DecisionModel.").formatted(iface.name()));
        }
        for (DotName chatModelSupplier : List.of(AgenticLangChain4jDotNames.CHAT_MODEL_SUPPLIER,
                AgenticLangChain4jDotNames.STREAMING_CHAT_MODEL_SUPPLIER)) {
            if (!iface.annotations(chatModelSupplier).isEmpty()) {
                throw new IllegalConfigurationException(("Decision agent '%s' must not declare a @%s method: a decision "
                        + "agent uses a decision model and no chat model").formatted(iface.name(),
                                chatModelSupplier.withoutPackagePrefix()));
            }
        }
        for (DotName unsupported : List.of(TOOLBOX, LangChain4jDotNames.MCP_TOOLBOX, AgenticLangChain4jDotNames.SKILLS)) {
            if (agentMethod.hasDeclaredAnnotation(unsupported)) {
                throw new IllegalConfigurationException(("Decision agent '%s' must not use @%s: a decision agent has no "
                        + "tools").formatted(iface.name(), unsupported.withoutPackagePrefix()));
            }
        }

        MethodInfo modelSupplier = decisionAgent.decisionModelSupplier();
        if (modelSupplier != null) {
            validateStaticMethod(modelSupplier, AgenticLangChain4jDotNames.DECISION_MODEL_SUPPLIER);
            validateAllowedReturnTypes(modelSupplier, Set.of(LangChain4jDotNames.DECISION_MODEL),
                    AgenticLangChain4jDotNames.DECISION_MODEL_SUPPLIER);
        }
        MethodInfo thresholdSupplier = decisionAgent.decisionThresholdSupplier();
        if (thresholdSupplier != null) {
            DotName annotation = AgenticLangChain4jDotNames.DECISION_THRESHOLD_SUPPLIER;
            validateStaticMethod(thresholdSupplier, annotation);
            validateAllowedReturnTypes(thresholdSupplier,
                    Set.of(DotNames.PRIMITIVE_DOUBLE, DotNames.DOUBLE), annotation);
            validateRequiredParameterTypes(thresholdSupplier, List.of(AgenticLangChain4jDotNames.THRESHOLD_CONTEXT),
                    annotation, index);
            if (!decisionAgent.booleanAnswer() && isAnswerTypeKnown(agentMethod, index)) {
                throw new IllegalConfigurationException(("Decision agent '%s' declares a @DecisionThresholdSupplier "
                        + "method, but it has no boolean answer, so a threshold would never be used: apply it to the "
                        + "probabilities of a YesNoAnswer, Choice or Scale answer instead").formatted(iface.name()));
            }
        }
    }

    /**
     * Whether the type of the answer of the given decision method is one of those analyzed at build time, so that
     * whether it has a {@code boolean} answer is known.
     */
    private static boolean isAnswerTypeKnown(MethodInfo agentMethod, IndexView index) {
        Type contentType = DecisionTypes.contentType(agentMethod.returnType());
        return DecisionTypes.isAnswerType(contentType, index)
                || DecisionTypes.objectAnswerType(contentType, index) != null;
    }

    private void validateA2AServerUrlSupplier(ClassInfo iface, IndexView index) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.A2A_SERVER_URL_SUPPLIER;
        List<AnnotationInstance> instances = iface.annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateNoMethodParameters(method, annotationToValidate, index);
            validateAllowedReturnTypes(method, Set.of(DotNames.STRING), annotationToValidate);
        }
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

    private void validateAgentListenerSupplier(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.AGENT_LISTENER_SUPPLIER;
        List<AnnotationInstance> instances = iface.annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(AgenticLangChain4jDotNames.AGENT_LISTENER), annotationToValidate);
        }
    }

    private void validateBeforeCall(ClassInfo iface, IndexView index) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.BEFORE_CALL;
        List<AnnotationInstance> instances = iface.annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
            validateAllowedReturnTypes(method, Set.of(DotNames.VOID), annotationToValidate);
            validateRequiredParameterTypes(method, List.of(AgenticLangChain4jDotNames.AGENTIC_SCOPE), annotationToValidate,
                    index);
        }
    }

    private void validateChatMemoryProviderSupplier(ClassInfo iface, IndexView index) {
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
            validateRequiredParameterTypes(method, List.of(DotNames.OBJECT), annotationToValidate, index);
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
            validateAllowedReturnTypes(method, Set.of(LangChain4jDotNames.RETRIEVER), annotationToValidate);
        }
    }

    private void validateErrorHandler(ClassInfo iface, IndexView index) {
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
            validateRequiredParameterTypes(method, List.of(AgenticLangChain4jDotNames.ERROR_CONTEXT), annotationToValidate,
                    index);
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

    /**
     * Validates {@code @HumanInTheLoop} usage on agent interfaces: the annotation must appear
     * on methods only (not at class level), and the annotated method must be static.
     */
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
        }
    }

    private void validateMcpClientSupplier(ClassInfo iface) {
        DotName annotationToValidate = AgenticLangChain4jDotNames.MCP_CLIENT_SUPPLIER;
        List<AnnotationInstance> instances = iface.annotations(annotationToValidate);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                log.warnf("Unhandled '@%s' annotation: '%s'", annotationToValidate.withoutPackagePrefix(), instance.target());
                continue;
            }
            MethodInfo method = instance.target().asMethod();
            validateStaticMethod(method, annotationToValidate);
        }
    }

    private void validateMcpToolBox(DetectedAiAgentBuildItem item) {
        if (!item.getMcpToolBoxMethods().isEmpty()) {
            if ((item.getMcpToolBoxMethods().size() != 1) || (item.getAgenticMethods().size() > 1)) {
                throw new IllegalConfigurationException(
                        "Currently, @McpToolBox can only be used on an Agent if the agent has a single method. This restriction will be lifted in the future. Offending class is '"
                                + item.getIface().name() + "'");
            }
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

    private void validateParallelExecutor(ClassInfo iface, IndexView index) {
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
            validateNoMethodParameters(method, annotationToValidate, index);
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
        return new AnnotationsImpliesAiServiceBuildItem(AI_SERVICE_AGENT_ANNOTATIONS);
    }

    /**
     * A decision agent is implemented by a call to a decision model, so it must not be registered as an AI service,
     * which would request a chat model for it.
     */
    @BuildStep
    void excludeDecisionAgentsFromAiServices(List<DetectedAiAgentBuildItem> agents,
            BuildProducer<ExcludeFromImpliedAiServiceBuildItem> excludedProducer) {
        for (DetectedAiAgentBuildItem agent : agents) {
            if (agent.isDecisionAgent()) {
                excludedProducer.produce(new ExcludeFromImpliedAiServiceBuildItem(agent.getIface().name()));
            }
        }
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
    void resolveLeafAgents(List<DetectedAiAgentBuildItem> detectedAgentBuildItems,
            DetectedNonAiAgentAsMapBuildItem detectedNonAiAgentAsMapBuildItem,
            AgenticRecorder recorder) {
        Set<String> leafAgentClassNames = new HashSet<>();
        for (DetectedAiAgentBuildItem bi : detectedAgentBuildItems) {
            boolean hasAgentAnnotation = bi.getAgenticMethods().stream()
                    .anyMatch(m -> m.hasAnnotation(AgenticLangChain4jDotNames.AGENT));
            if (hasAgentAnnotation) {
                leafAgentClassNames.add(bi.getIface().name().toString());
            }
        }
        for (DotName className : detectedNonAiAgentAsMapBuildItem.getClassToNonAiAgentMethodsMap().keySet()) {
            leafAgentClassNames.add(className.toString());
        }
        recorder.setLeafAgentClassNames(leafAgentClassNames);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerAgenticScopeDeserializationTypes(
            DetectedAiAgentAsMapBuildItem detectedAiAgentAsMapBuildItem,
            DetectedNonAiAgentAsMapBuildItem detectedNonAiAgentAsMapBuildItem,
            CombinedIndexBuildItem indexBuildItem,
            AgenticRecorder recorder) {
        Set<String> domainClassNames = new HashSet<>();
        IndexView index = indexBuildItem.getIndex();

        collectDomainTypes(detectedAiAgentAsMapBuildItem.getIfaceToAgentMethodsMap(), domainClassNames, index);
        collectDomainTypes(detectedNonAiAgentAsMapBuildItem.getClassToNonAiAgentMethodsMap(), domainClassNames, index);

        if (!domainClassNames.isEmpty()) {
            recorder.registerAgenticScopeDeserializationTypes(domainClassNames);
        }
    }

    private static void collectDomainTypes(Map<DotName, List<MethodInfo>> methodsMap, Set<String> domainClassNames,
            IndexView index) {
        for (List<MethodInfo> methods : methodsMap.values()) {
            for (MethodInfo method : methods) {
                if (method.declaringClass().name().toString().startsWith(AGENTIC_PACKAGE_PREFIX)) {
                    continue;
                }
                collectDomainType(method.returnType(), domainClassNames);
                // the parameters resolved from CDI are never stored in the AgenticScope
                for (MethodParameterInfo parameter : ValidationUtil.nonCdiParameters(method, index)) {
                    collectDomainType(parameter.type(), domainClassNames);
                }
            }
        }
    }

    private static void collectDomainType(Type type, Set<String> domainClassNames) {
        if (type.kind() == Type.Kind.VOID || type.kind() == Type.Kind.PRIMITIVE) {
            return;
        }
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            DotName name = type.name();
            if (AgenticLangChain4jDotNames.RESULT_WITH_AGENTIC_SCOPE.equals(name)
                    || DotNames.LIST.equals(name) || DotNames.SET.equals(name)
                    || DotNames.OBJECT.equals(name)) {
                for (Type arg : type.asParameterizedType().arguments()) {
                    collectDomainType(arg, domainClassNames);
                }
                return;
            }
        }
        String className = type.name().toString();
        if (className.startsWith("java.") || className.startsWith("javax.")
                || className.startsWith("jakarta.") || className.startsWith("dev.langchain4j.")
                || className.startsWith("io.quarkiverse.langchain4j.")) {
            return;
        }
        domainClassNames.add(className);
    }

    @BuildStep
    SkipToolBoxProcessingBuildItem skipToolBoxForAgents() {
        return new SkipToolBoxProcessingBuildItem(methodInfo -> {
            for (DotName dotName : AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS) {
                if (methodInfo.hasAnnotation(dotName)) {
                    return true;
                }
            }
            return false;
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
    @Record(ExecutionTime.STATIC_INIT)
    void toolBoxSupport(List<DetectedAiAgentBuildItem> detectedAgentBuildItems, AgenticRecorder recorder,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer) {
        Map<String, List<String>> agentsWithToolBox = new HashMap<>();
        for (DetectedAiAgentBuildItem bi : detectedAgentBuildItems) {
            if (!bi.getToolBoxMethods().isEmpty()) {
                boolean hasToolsSupplier = bi.getIface().methods().stream()
                        .anyMatch(m -> m.hasAnnotation(AgenticLangChain4jDotNames.TOOL_SUPPLIER));
                if (hasToolsSupplier) {
                    throw new IllegalConfigurationException(
                            "Agent interface '" + bi.getIface().name()
                                    + "' cannot use both @ToolsSupplier and @ToolBox. "
                                    + "Use one or the other to specify tools.");
                }
                List<String> toolClassNames = new ArrayList<>();
                for (MethodInfo method : bi.getToolBoxMethods()) {
                    AnnotationInstance toolBoxAnnotation = method.declaredAnnotation(TOOLBOX);
                    if (toolBoxAnnotation != null && toolBoxAnnotation.value() != null) {
                        for (Type toolClass : toolBoxAnnotation.value().asClassArray()) {
                            String className = toolClass.name().toString();
                            if (!toolClassNames.contains(className)) {
                                toolClassNames.add(className);
                                unremovableBeanProducer.produce(
                                        UnremovableBeanBuildItem.beanTypes(toolClass.name()));
                            }
                        }
                    }
                }
                if (!toolClassNames.isEmpty()) {
                    agentsWithToolBox.put(bi.getIface().name().toString(), toolClassNames);
                }
            }
        }
        recorder.setAgentsWithToolBox(agentsWithToolBox);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void skillsSupport(List<DetectedAiAgentBuildItem> detectedAgentBuildItems, AgenticRecorder recorder) {
        Map<String, List<String>> agentsWithSkills = new HashMap<>();
        for (DetectedAiAgentBuildItem bi : detectedAgentBuildItems) {
            if (bi.getSkillsMethods().isEmpty()) {
                continue;
            }
            List<String> skillNames = new ArrayList<>();
            for (MethodInfo method : bi.getSkillsMethods()) {
                AnnotationInstance skillsAnnotation = method.declaredAnnotation(AgenticLangChain4jDotNames.SKILLS);
                if (skillsAnnotation != null && skillsAnnotation.value() != null) {
                    for (String name : skillsAnnotation.value().asStringArray()) {
                        if (!skillNames.contains(name)) {
                            skillNames.add(name);
                        }
                    }
                }
            }
            agentsWithSkills.put(bi.getIface().name().toString(), skillNames);
        }
        recorder.setAgentsWithSkills(agentsWithSkills);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    void validateSkillNames(List<DetectedAiAgentBuildItem> detectedAgentBuildItems, AgenticRecorder recorder,
            BeanContainerBuildItem beanContainer) {
        Set<String> allSkillNames = new LinkedHashSet<>();
        for (DetectedAiAgentBuildItem bi : detectedAgentBuildItems) {
            for (MethodInfo method : bi.getSkillsMethods()) {
                AnnotationInstance skillsAnnotation = method.declaredAnnotation(AgenticLangChain4jDotNames.SKILLS);
                if (skillsAnnotation != null && skillsAnnotation.value() != null) {
                    for (String name : skillsAnnotation.value().asStringArray()) {
                        allSkillNames.add(name);
                    }
                }
            }
        }
        if (!allSkillNames.isEmpty()) {
            recorder.validateSkillNames(beanContainer.getValue(), allSkillNames);
        }
    }

    /**
     * Returns the parameters resolved from CDI of the static methods that langchain4j-agentic invokes on the detected
     * agents: the helper methods, like suppliers, conditions and outputs, and the static agent methods of non-AI and
     * human-in-the-loop agents, declared on the agent types or on their supertypes. A parameter is resolved from CDI if
     * it carries {@code @CdiBean} or any {@code @jakarta.inject.Qualifier}-annotated annotation.
     */
    private static List<MethodParameterInfo> cdiResolvableParams(List<DetectedAiAgentBuildItem> aiAgents,
            DetectedNonAiAgentAsMapBuildItem nonAiAgents, IndexView index) {
        Set<ClassInfo> agentTypes = new LinkedHashSet<>();
        for (DetectedAiAgentBuildItem agent : aiAgents) {
            agentTypes.addAll(ValidationUtil.typeHierarchy(agent.getIface(), index));
        }
        for (List<MethodInfo> agentMethods : nonAiAgents.getClassToNonAiAgentMethodsMap().values()) {
            for (MethodInfo agentMethod : agentMethods) {
                agentTypes.addAll(ValidationUtil.typeHierarchy(agentMethod.declaringClass(), index));
            }
        }
        List<MethodParameterInfo> result = new ArrayList<>();
        for (ClassInfo agentType : agentTypes) {
            if (agentType.name().toString().startsWith(AGENTIC_PACKAGE_PREFIX)) {
                continue;
            }
            for (MethodInfo method : agentType.methods()) {
                if (!Modifier.isStatic(method.flags())
                        || CDI_RESOLVABLE_METHOD_ANNOTATIONS.stream().noneMatch(method::hasDeclaredAnnotation)) {
                    continue;
                }
                for (MethodParameterInfo param : method.parameters()) {
                    if (ValidationUtil.isCdiResolvable(param, index)) {
                        result.add(param);
                    }
                }
            }
        }
        return result;
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void registerSupplierParameterResolver(List<DetectedAiAgentBuildItem> agents,
            DetectedNonAiAgentAsMapBuildItem nonAiAgents,
            CombinedIndexBuildItem indexBuildItem,
            AgenticRecorder recorder) {
        IndexView index = indexBuildItem.getIndex();
        Set<String> qualifierNames = new HashSet<>();
        for (MethodParameterInfo param : cdiResolvableParams(agents, nonAiAgents, index)) {
            for (AnnotationInstance qualifier : ValidationUtil.qualifiers(param, index)) {
                qualifierNames.add(qualifier.name().toString());
            }
        }
        recorder.registerSupplierParameterResolver(qualifierNames);
    }

    /**
     * Marks the beans of the parameters resolved from CDI as unremovable, walking the full type hierarchy of the
     * agents so that parameters declared on parent types are not removed by Arc's unused-bean pruning.
     */
    @BuildStep
    void markCdiBeanParametersAsUnremovable(
            List<DetectedAiAgentBuildItem> detectedAiAgentBuildItems,
            DetectedNonAiAgentAsMapBuildItem nonAiAgents,
            CombinedIndexBuildItem indexBuildItem,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer) {
        for (MethodParameterInfo param : cdiResolvableParams(detectedAiAgentBuildItems, nonAiAgents,
                indexBuildItem.getIndex())) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(param.type().name()));
        }
    }

    /**
     * Requests the model beans of the providers for the parameters resolved from CDI whose type is a model, like
     * {@code @CdiBean DecisionModel} or {@code @ModelName("planner") ChatModel}: the providers only create the model
     * beans that are requested, and these parameters are not injection points. Parameters with other qualifiers are
     * expected to be resolved by beans of the application.
     */
    @BuildStep
    void requestModelBeansOfCdiResolvableParams(List<DetectedAiAgentBuildItem> aiAgents,
            DetectedNonAiAgentAsMapBuildItem nonAiAgents,
            CombinedIndexBuildItem indexBuildItem,
            BuildProducer<RequestChatModelBeanBuildItem> requestChatModelProducer,
            BuildProducer<RequestDecisionModelBeanBuildItem> requestDecisionModelProducer,
            BuildProducer<RequestModerationModelBeanBuildItem> requestModerationModelProducer,
            BuildProducer<RequestImageModelBeanBuildItem> requestImageModelProducer) {
        IndexView index = indexBuildItem.getIndex();
        Set<String> chatModels = new HashSet<>();
        Set<String> decisionModels = new HashSet<>();
        Set<String> moderationModels = new HashSet<>();
        Set<String> imageModels = new HashSet<>();
        for (MethodParameterInfo param : cdiResolvableParams(aiAgents, nonAiAgents, index)) {
            String modelName = modelName(ValidationUtil.qualifiers(param, index));
            if (modelName == null) {
                continue;
            }
            DotName type = param.type().name();
            if (type.equals(LangChain4jDotNames.CHAT_MODEL)) {
                chatModels.add(modelName);
            } else if (type.equals(LangChain4jDotNames.DECISION_MODEL)) {
                decisionModels.add(modelName);
            } else if (type.equals(LangChain4jDotNames.MODERATION_MODEL)) {
                moderationModels.add(modelName);
            } else if (type.equals(LangChain4jDotNames.IMAGE_MODEL)) {
                imageModels.add(modelName);
            }
        }
        chatModels.forEach(name -> requestChatModelProducer.produce(new RequestChatModelBeanBuildItem(name)));
        decisionModels.forEach(name -> requestDecisionModelProducer.produce(new RequestDecisionModelBeanBuildItem(name)));
        moderationModels
                .forEach(name -> requestModerationModelProducer.produce(new RequestModerationModelBeanBuildItem(name)));
        imageModels.forEach(name -> requestImageModelProducer.produce(new RequestImageModelBeanBuildItem(name)));
    }

    /**
     * The name of the model selected by the given qualifiers: the default model without qualifier, the one of
     * {@code @ModelName}, or {@code null} for other qualifiers.
     */
    private static String modelName(List<AnnotationInstance> qualifiers) {
        if (qualifiers.isEmpty()) {
            return NamedConfigUtil.DEFAULT_NAME;
        }
        if (qualifiers.size() == 1 && qualifiers.get(0).name().equals(LangChain4jDotNames.MODEL_NAME)) {
            return qualifiers.get(0).value().asString();
        }
        return null;
    }

    /**
     * Fails the build when a parameter resolved from CDI does not resolve to exactly one bean, instead of failing when
     * the agent invokes the method declaring it.
     */
    @BuildStep
    void validateCdiResolvableParams(List<DetectedAiAgentBuildItem> aiAgents,
            DetectedNonAiAgentAsMapBuildItem nonAiAgents,
            CombinedIndexBuildItem indexBuildItem,
            ValidationPhaseBuildItem validationPhase,
            BuildProducer<ValidationErrorBuildItem> validationErrors) {
        IndexView index = indexBuildItem.getIndex();
        BeanResolver beanResolver = validationPhase.getBeanResolver();
        List<Throwable> errors = new ArrayList<>();
        for (MethodParameterInfo param : cdiResolvableParams(aiAgents, nonAiAgents, index)) {
            Set<AnnotationInstance> qualifiers = new HashSet<>(ValidationUtil.qualifiers(param, index));
            if (qualifiers.isEmpty()) {
                qualifiers.add(AnnotationInstance.builder(io.quarkus.arc.processor.DotNames.DEFAULT).build());
            }
            // like at runtime, the bean is looked up by the raw type of the parameter
            Type type = ClassType.create(param.type().name());
            Set<BeanInfo> beans = beanResolver.resolveBeans(type, qualifiers);
            String parameterDescription = "parameter no.%d of method '%s' of class '%s'".formatted(
                    param.position(), param.method().name(), param.method().declaringClass().name());
            if (beans.isEmpty()) {
                errors.add(new IllegalConfigurationException(
                        "No CDI bean of type '%s' with qualifiers %s is available for %s".formatted(
                                type.name(), qualifiers, parameterDescription)));
                continue;
            }
            BeanInfo bean;
            try {
                bean = beanResolver.resolveAmbiguity(beans);
            } catch (RuntimeException e) {
                errors.add(new IllegalConfigurationException(
                        "Ambiguous CDI beans of type '%s' with qualifiers %s for %s: %s".formatted(
                                type.name(), qualifiers, parameterDescription, beans)));
                continue;
            }
            if (BuiltinScope.DEPENDENT.is(bean.getScope())) {
                log.warnf("The @Dependent bean %s is created again, and never destroyed, at each invocation of %s. "
                        + "Consider giving it a normal scope, like @ApplicationScoped.", bean, parameterDescription);
            }
        }
        if (!errors.isEmpty()) {
            validationErrors.produce(new ValidationErrorBuildItem(errors));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void cdiSupport(List<DetectedAiAgentBuildItem> detectedAiAgentBuildItems, AgenticRecorder recorder,
            InterceptorResolverBuildItem interceptorResolverBuildItem,
            BeanDiscoveryFinishedBuildItem beanDiscovery,
            CombinedIndexBuildItem indexBuildItem,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            BuildProducer<RequestChatModelBeanBuildItem> requestChatModelBeanProducer,
            BuildProducer<RequestDecisionModelBeanBuildItem> requestDecisionModelBeanProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer) {

        Set<DotName> interceptorBindings = interceptorResolverBuildItem.getInterceptorBindings();
        IndexView index = indexBuildItem.getIndex();
        Set<String> requestedChatModelNames = new HashSet<>();
        Set<String> requestedDecisionModelNames = new HashSet<>();
        for (DetectedAiAgentBuildItem detectedAiAgentBuildItem : detectedAiAgentBuildItems) {
            AiAgentCreateInfo.ChatModelInfo chatModelInfo;
            if (detectedAiAgentBuildItem.getChatModelSupplier() != null) {
                chatModelInfo = new AiAgentCreateInfo.ChatModelInfo.FromAnnotation();
            } else if (requiresChatModel(detectedAiAgentBuildItem)) {
                String chatModelName = detectedAiAgentBuildItem.getModelName() != null
                        ? detectedAiAgentBuildItem.getModelName()
                        : NamedConfigUtil.DEFAULT_NAME;
                requestedChatModelNames.add(chatModelName);
                chatModelInfo = new AiAgentCreateInfo.ChatModelInfo.FromBeanWithName(chatModelName);
            } else {
                chatModelInfo = new AiAgentCreateInfo.ChatModelInfo.NotNeeded();
            }

            AiAgentCreateInfo.DecisionModelInfo decisionModelInfo = null;
            if (detectedAiAgentBuildItem.isDecisionAgent()) {
                if (detectedAiAgentBuildItem.getDecisionAgent().decisionModelSupplier() != null) {
                    decisionModelInfo = new AiAgentCreateInfo.DecisionModelInfo.FromAnnotation();
                } else {
                    String decisionModelName = detectedAiAgentBuildItem.getModelName() != null
                            ? detectedAiAgentBuildItem.getModelName()
                            : NamedConfigUtil.DEFAULT_NAME;
                    requestedDecisionModelNames.add(decisionModelName);
                    decisionModelInfo = new AiAgentCreateInfo.DecisionModelInfo.FromBeanWithName(decisionModelName);
                }
            }

            boolean hasInterceptorBindings = hasAnyInterceptorBindings(detectedAiAgentBuildItem, interceptorBindings, index);
            String implClassName = implClassName(detectedAiAgentBuildItem);

            boolean hasMcpToolBox = !detectedAiAgentBuildItem.getMcpToolBoxMethods().isEmpty();

            SyntheticBeanBuildItem.ExtendedBeanConfigurator beanConfigurator;
            if (hasInterceptorBindings) {
                beanConfigurator = SyntheticBeanBuildItem
                        .configure(DotName.createSimple(implClassName))
                        .addType(ClassType.create(detectedAiAgentBuildItem.getIface().name()));
            } else {
                beanConfigurator = SyntheticBeanBuildItem
                        .configure(detectedAiAgentBuildItem.getIface().name());
            }
            beanConfigurator
                    .forceApplicationClass()
                    .unremovable()
                    .createWith(recorder
                            .createAiAgent(
                                    new AiAgentCreateInfo(detectedAiAgentBuildItem.getIface().toString(), chatModelInfo,
                                            hasInterceptorBindings, hasMcpToolBox, decisionModelInfo)))
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class);
            if (hasInterceptorBindings) {
                beanConfigurator.injectInterceptionProxy();
            }
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
            if (decisionModelInfo instanceof AiAgentCreateInfo.DecisionModelInfo.FromBeanWithName f) {
                beanConfigurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.DECISION_MODEL),
                        modelQualifier(f.name()));
            }
            beanConfigurator.addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                    new Type[] { ClassType.create(LangChain4jDotNames.TOOL_PROVIDER) }, null));

            // AgentListener CDI beans are additive — wired into all agents
            beanConfigurator.addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                    new Type[] { ClassType.create(AgenticLangChain4jDotNames.AGENT_LISTENER) }, null));

            syntheticBeanProducer.produce(beanConfigurator.done());
        }

        // Mark AgentListener beans as unremovable (global check, outside per-agent loop)
        if (!beanDiscovery.beanStream().withBeanType(AgenticLangChain4jDotNames.AGENT_LISTENER).isEmpty()) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(AgenticLangChain4jDotNames.AGENT_LISTENER));
        }

        if (!detectedAiAgentBuildItems.isEmpty()) {
            beanDiscovery.beanStream()
                    .withBeanType(AgenticLangChain4jDotNames.AGENT_LISTENER)
                    .forEach(bean -> {
                        DotName scope = bean.getScope().getDotName();
                        if (!scope.equals(BuiltinScope.APPLICATION.getInfo().getDotName())
                                && !scope.equals(BuiltinScope.SINGLETON.getInfo().getDotName())) {
                            throw new IllegalConfigurationException(
                                    "CDI bean of type 'AgentListener' is @" + scope.withoutPackagePrefix()
                                            + " but must be @ApplicationScoped or @Singleton. "
                                            + "Agent synthetic beans are created at application startup "
                                            + "when no request context is active.");
                        }
                    });
        }

        requestedChatModelNames.forEach(name -> requestChatModelBeanProducer.produce(new RequestChatModelBeanBuildItem(name)));
        requestedDecisionModelNames
                .forEach(name -> requestDecisionModelBeanProducer.produce(new RequestDecisionModelBeanBuildItem(name)));
    }

    private static AnnotationInstance modelQualifier(String modelName) {
        if (NamedConfigUtil.isDefault(modelName)) {
            return AnnotationInstance.builder(Default.class).build();
        }
        return AnnotationInstance.builder(ModelName.class).add("value", modelName).build();
    }

    private static String implClassName(DetectedAiAgentBuildItem agent) {
        return agent.getIface().name().toString()
                + (agent.isDecisionAgent() ? "$$QuarkusDecisionAgentImpl" : "$$QuarkusAgentImpl");
    }

    private static boolean isInterceptorBindingAnnotation(AnnotationInstance ann, IndexView index) {
        ClassInfo annClass = index.getClassByName(ann.name());
        if (annClass != null) {
            return annClass.hasAnnotation(INTERCEPTOR_BINDING);
        }
        try {
            Class<?> annClazz = Class.forName(ann.name().toString(), false,
                    Thread.currentThread().getContextClassLoader());
            return annClazz.isAnnotationPresent(jakarta.interceptor.InterceptorBinding.class);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean hasAnyInterceptorBindings(DetectedAiAgentBuildItem agent,
            Set<DotName> interceptorBindings, IndexView index) {
        Set<ClassInfo> hierarchy = ValidationUtil.transitiveInterfaces(agent.getIface(), index);

        // Class-level check: walk the full interface hierarchy for class-level interceptor bindings
        for (ClassInfo classInfo : hierarchy) {
            if (ValidationUtil.hasAnnotation(classInfo.declaredAnnotations(), interceptorBindings)) {
                return true;
            }
        }

        // Method-level check: check each agentic method, then look up the same method signature
        // on parent interfaces (handles case where @Agent is redeclared on child interface and
        // the interceptor binding is only on the parent interface's version of the method)
        for (MethodInfo method : agent.getAgenticMethods()) {
            if (ValidationUtil.hasAnnotation(method.annotations(), interceptorBindings)) {
                return true;
            }
            for (ClassInfo classInfo : hierarchy) {
                if (classInfo.name().equals(agent.getIface().name())) {
                    continue; // already covered by method.annotations() above
                }
                // Look up matching method on this parent interface by name + parameter types.
                // Returns null if the parent doesn't declare that method — safe to ignore.
                Type[] paramTypes = method.parameterTypes()
                        .toArray(new Type[0]);
                MethodInfo parentMethod = classInfo.method(method.name(), paramTypes);
                if (parentMethod != null
                        && ValidationUtil.hasAnnotation(parentMethod.annotations(), interceptorBindings)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean requiresChatModel(DetectedAiAgentBuildItem agent) {
        if (agent.isDecisionAgent()) {
            return false;
        }
        return agent.getAgenticMethods().stream()
                .flatMap(m -> m.annotations().stream())
                .map(AnnotationInstance::name)
                .anyMatch(CHAT_MODEL_AGENT_ANNOTATIONS::contains);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void generateAgentImplementations(List<DetectedAiAgentBuildItem> detectedAiAgentBuildItems,
            CombinedIndexBuildItem indexBuildItem,
            AgenticRecorder recorder,
            BuildProducer<GeneratedBeanBuildItem> generatedBeanProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer) {

        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeanProducer);
        IndexView index = indexBuildItem.getIndex();
        Map<String, AgentClassCreateInfo> metadata = new HashMap<>();

        Map<String, DecisionAgentInfo> decisionAgents = new HashMap<>();

        for (DetectedAiAgentBuildItem bi : detectedAiAgentBuildItems) {
            String ifaceName = bi.getIface().name().toString();
            String implClassName = implClassName(bi);
            boolean isLeaf = bi.getAgenticMethods().stream()
                    .anyMatch(m -> m.hasAnnotation(AgenticLangChain4jDotNames.AGENT));

            generateAgentClass(classOutput, bi.getIface(), implClassName, isLeaf, bi.isDecisionAgent(), index);

            metadata.put(ifaceName, new AgentClassCreateInfo(implClassName));
            reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(implClassName).build());

            DetectedAiAgentBuildItem.DecisionAgent decisionAgent = bi.getDecisionAgent();
            if (decisionAgent != null) {
                decisionAgents.put(ifaceName, new DecisionAgentInfo(decisionAgent.agentName(),
                        decisionAgent.booleanAnswer(), decisionAgent.decisionThresholdSupplier() != null));
            }
        }

        recorder.setAgentClassMetadata(metadata);
        recorder.setDecisionAgents(decisionAgents);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void validateDecisionAgentConfig(AgenticRecorder recorder) {
        recorder.validateDecisionAgentConfig();
    }

    /**
     * Registers for reflection what upstream reads by reflection to implement a decision agent, which is not loaded at
     * build time: the agent interface, whose {@code @Agent} method is looked up, the object returned by the method, the
     * enums of its answers, whose constant fields are read for {@code @Description}, and the objects sent to the model,
     * which are converted with the JSON codec.
     */
    @BuildStep
    void registerDecisionAgentsForReflection(List<DetectedAiAgentBuildItem> agents, CombinedIndexBuildItem indexBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer) {
        IndexView index = indexBuildItem.getIndex();
        Set<String> interfaces = new HashSet<>();
        Set<String> types = new HashSet<>();
        for (DetectedAiAgentBuildItem agent : agents) {
            if (agent.isDecisionAgent()) {
                for (ClassInfo iface : ValidationUtil.transitiveInterfaces(agent.getIface(), index)) {
                    interfaces.add(iface.name().toString());
                }
                DecisionTypes.reflectiveTypes(agent.getDecisionAgent().agentMethod(), index)
                        .forEach(type -> types.add(type.toString()));
            }
        }
        if (!interfaces.isEmpty()) {
            reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(interfaces.toArray(new String[0]))
                    .methods().reason(AgenticProcessor.class.getName()).build());
        }
        if (!types.isEmpty()) {
            reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(types.toArray(new String[0]))
                    .constructors().methods().fields().reason(AgenticProcessor.class.getName()).build());
        }
    }

    /**
     * Generates a Quarkus agent implementation class for the given agent interface, with a cade like the following:
     *
     * <pre>
     * {@code
     * public class CarConditionFeedbackAgent$$QuarkusAgentImpl
     *         extends AbstractQuarkusAgent
     *         implements CarConditionFeedbackAgent,
     *         ChatMemoryAccess,
     *         AiServiceResponseReceivedListener {
     *     private static Method METHOD_agent_analyzeForCondition_1;
     *
     *     public CarConditionFeedbackAgent$$QuarkusAgentImpl() {
     *     }
     *
     *     public CarConditionFeedbackAgent$$QuarkusAgentImpl(InternalAgent var1) {
     *         super(var1);
     *     }
     *
     *     static {
     *         try {
     *             Class[] var0 = new Class[] { CarInfo.class, Integer.class, FeedbackAnalysisResults.class, String.class };
     *             METHOD_agent_analyzeForCondition_1 = CarConditionFeedbackAgent.class.getMethod("analyzeForCondition", var0);
     *         } catch (Exception var2) {
     *             throw (Throwable) (new RuntimeException("Failed to initialize agent method references", var2));
     *         }
     *     }
     *
     *     public CarConditions analyzeForCondition(CarInfo var1, Integer var2, FeedbackAnalysisResults var3, String var4) {
     *         InvocationHandler var10 = (InvocationHandler) this.agent;
     *         Method var11 = METHOD_agent_analyzeForCondition_1;
     *         Object[] var5 = new Object[4];
     *         CarInfo var6 = var1;
     *         var5[0] = var6;
     *         Integer var7 = var2;
     *         var5[1] = var7;
     *         FeedbackAnalysisResults var8 = var3;
     *         var5[2] = var8;
     *         String var9 = var4;
     *         var5[3] = var9;
     *         return (CarConditions) var10.invoke(this, var11, var5);
     *     }
     * }
     * }
     * </pre>
     */
    private void generateAgentClass(ClassOutput classOutput, ClassInfo agentIface, String implClassName,
            boolean isLeaf, boolean isDecisionAgent, IndexView index) {
        String ifaceName = agentIface.name().toString();
        // the base class of a decision agent brings no chat interface
        Class<?> superClass = isDecisionAgent ? AbstractQuarkusDecisionAgent.class : AbstractQuarkusAgent.class;

        List<String> interfaces = new ArrayList<>();
        interfaces.add(ifaceName);
        if (!isDecisionAgent && isLeaf) {
            interfaces.add(ChatMemoryAccess.class.getName());
            interfaces.add(AiServiceResponseReceivedListener.class.getName());
        } else if (!isDecisionAgent) {
            interfaces.add(AgenticScopeAccess.class.getName());
        }

        try (ClassCreator cc = ClassCreator.builder()
                .classOutput(classOutput)
                .className(implClassName)
                .superClass(superClass.getName())
                .interfaces(interfaces.toArray(new String[0]))
                .build()) {

            try (MethodCreator ctor = cc.getMethodCreator(MethodDescriptor.INIT, "V")) {
                ctor.setModifiers(Modifier.PUBLIC);
                ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(superClass), ctor.getThis());
                ctor.returnVoid();
            }

            try (MethodCreator ctor = cc.getMethodCreator(MethodDescriptor.INIT, "V",
                    InternalAgent.class.getName())) {
                ctor.setModifiers(Modifier.PUBLIC);
                ctor.invokeSpecialMethod(
                        MethodDescriptor.ofConstructor(superClass, InternalAgent.class),
                        ctor.getThis(), ctor.getMethodParam(0));
                ctor.returnVoid();
            }

            FieldDescriptor handlerField = FieldDescriptor.of(implClassName, "agent",
                    InternalAgent.class.getName());

            // Stamp class-level interceptor binding annotations from parent interfaces onto the
            // generated class so Arc sees them when resolving the interception proxy.
            // Method-level bindings are handled per-method in generateHandlerDelegation.
            Set<ClassInfo> hierarchy = ValidationUtil.transitiveInterfaces(agentIface, index);
            for (ClassInfo parentIface : hierarchy) {
                if (parentIface.name().equals(agentIface.name())) {
                    continue;
                }
                for (AnnotationInstance ann : parentIface.declaredAnnotations()) {
                    if (ann.target().kind() != AnnotationTarget.Kind.CLASS) {
                        continue;
                    }
                    ClassInfo annClass = index.getClassByName(ann.name());
                    if (annClass != null && annClass.hasAnnotation(INTERCEPTOR_BINDING)) {
                        cc.addAnnotation(ann);
                    }
                }
            }

            List<MethodToDelegate> methodsToDelegate = collectMethodsToDelegate(agentIface, index);

            Map<String, FieldDescriptor> methodFields = new HashMap<>();
            for (MethodToDelegate m : methodsToDelegate) {
                FieldDescriptor fd = cc.getFieldCreator(m.fieldName, Method.class)
                        .setModifiers(Modifier.PRIVATE | Modifier.STATIC)
                        .getFieldDescriptor();
                methodFields.put(m.fieldName, fd);
            }

            generateStaticInitializer(cc, methodsToDelegate, methodFields);

            for (MethodToDelegate m : methodsToDelegate) {
                generateHandlerDelegation(cc, handlerField, methodFields.get(m.fieldName), m);
            }
        }
    }

    private record MethodToDelegate(
            String fieldName,
            String declaringClassName,
            String methodName,
            String returnTypeName,
            String returnWrapperTypeName,
            String[] paramTypeNames,
            List<AnnotationInstance> annotations) {
    }

    private static List<MethodToDelegate> collectMethodsToDelegate(ClassInfo agentIface, IndexView index) {
        List<MethodToDelegate> methods = new ArrayList<>();
        collectInterfaceMethods(agentIface, methods, index, new HashSet<>());
        return methods;
    }

    private static void collectInterfaceMethods(ClassInfo iface, List<MethodToDelegate> methods,
            IndexView index, Set<String> seen) {
        String declaringClassName = iface.name().toString();
        for (MethodInfo mi : iface.methods()) {
            if (Modifier.isStatic(mi.flags()) || !Modifier.isAbstract(mi.flags())) {
                continue;
            }
            String methodSig = mi.name() + mi.descriptor();
            if (!seen.add(methodSig)) {
                continue;
            }
            String fieldName = "METHOD_agent_" + mi.name() + "_" + seen.size();
            String returnTypeName = mi.returnType().name().toString();
            String returnWrapperTypeName = null;
            if (mi.returnType().kind() == Type.Kind.VOID) {
                returnTypeName = void.class.getName();
            } else if (mi.returnType().kind() == Type.Kind.PRIMITIVE) {
                returnTypeName = mi.returnType().asPrimitiveType().primitive().name().toLowerCase();
                returnWrapperTypeName = PrimitiveType.box(mi.returnType().asPrimitiveType()).name().toString();
            }

            String[] paramTypeNames = new String[mi.parametersCount()];
            for (int i = 0; i < mi.parametersCount(); i++) {
                Type pt = mi.parameterType(i);
                if (pt.kind() == Type.Kind.PRIMITIVE) {
                    paramTypeNames[i] = pt.asPrimitiveType().primitive().name().toLowerCase();
                } else {
                    paramTypeNames[i] = pt.name().toString();
                }
            }

            List<AnnotationInstance> interceptorBindingAnnotations = mi.declaredAnnotations().stream()
                    .filter(ann -> isInterceptorBindingAnnotation(ann, index))
                    .toList();
            methods.add(new MethodToDelegate(fieldName, declaringClassName, mi.name(), returnTypeName,
                    returnWrapperTypeName, paramTypeNames, interceptorBindingAnnotations));
        }

        for (DotName superIface : iface.interfaceNames()) {
            ClassInfo superIfaceInfo = index.getClassByName(superIface);
            if (superIfaceInfo != null) {
                collectInterfaceMethods(superIfaceInfo, methods, index, seen);
            }
        }
    }

    private static void generateStaticInitializer(ClassCreator cc, List<MethodToDelegate> methods,
            Map<String, FieldDescriptor> methodFields) {
        if (methods.isEmpty()) {
            return;
        }

        try (MethodCreator clinit = cc.getMethodCreator(MethodDescriptor.ofMethod(
                cc.getClassName(), "<clinit>", void.class))) {
            clinit.setModifiers(Modifier.STATIC);

            TryBlock tryBlock = clinit.tryBlock();

            for (MethodToDelegate m : methods) {
                FieldDescriptor fd = methodFields.get(m.fieldName);
                ResultHandle declaringClass = tryBlock.loadClass(m.declaringClassName);
                ResultHandle paramTypesArray = tryBlock.newArray(Class.class, m.paramTypeNames.length);
                for (int i = 0; i < m.paramTypeNames.length; i++) {
                    tryBlock.writeArrayValue(paramTypesArray, i, tryBlock.loadClass(m.paramTypeNames[i]));
                }
                ResultHandle method = tryBlock.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(Class.class, "getMethod", Method.class, String.class, Class[].class),
                        declaringClass, tryBlock.load(m.methodName), paramTypesArray);
                tryBlock.writeStaticField(fd, method);
            }

            CatchBlockCreator catchBlock = tryBlock.addCatch(Exception.class);
            ResultHandle rte = catchBlock.newInstance(
                    MethodDescriptor.ofConstructor(RuntimeException.class, String.class, Throwable.class),
                    catchBlock.load("Failed to initialize agent method references"), catchBlock.getCaughtException());
            catchBlock.throwException(rte);

            clinit.returnVoid();
        }
    }

    private static void generateHandlerDelegation(ClassCreator cc, FieldDescriptor handlerField,
            FieldDescriptor methodField, MethodToDelegate m) {
        String[] paramTypes = m.paramTypeNames;
        try (MethodCreator mc = cc.getMethodCreator(m.methodName, m.returnTypeName, paramTypes)) {
            mc.setModifiers(Modifier.PUBLIC);

            for (AnnotationInstance ann : m.annotations) {
                mc.addAnnotation(ann);
            }

            ResultHandle handler = mc.readInstanceField(handlerField, mc.getThis());
            ResultHandle invocationHandler = mc.checkCast(handler, InvocationHandler.class);
            ResultHandle methodHandle = mc.readStaticField(methodField);

            ResultHandle argsArray;
            if (paramTypes.length == 0) {
                argsArray = mc.loadNull();
            } else {
                argsArray = mc.newArray(Object.class, paramTypes.length);
                for (int i = 0; i < paramTypes.length; i++) {
                    mc.writeArrayValue(argsArray, i, mc.checkCast(mc.getMethodParam(i), Object.class));
                }
            }

            ResultHandle result = mc.invokeInterfaceMethod(HANDLER_INVOKE,
                    invocationHandler, mc.getThis(), methodHandle, argsArray);

            if ("void".equals(m.returnTypeName)) {
                mc.returnVoid();
            } else if (m.returnWrapperTypeName != null) {
                mc.returnValue(mc.smartCast(mc.checkCast(result, m.returnWrapperTypeName), m.returnTypeName));
            } else {
                mc.returnValue(mc.checkCast(result, m.returnTypeName));
            }
        }
    }

    private static String extractModelName(List<MethodInfo> agenticMethods) {
        for (MethodInfo method : agenticMethods) {
            if (!method.hasAnnotation(AgenticLangChain4jDotNames.AGENT)) {
                continue;
            }
            AnnotationInstance modelNameAnnotation = method.annotation(LangChain4jDotNames.MODEL_NAME);
            if (modelNameAnnotation != null) {
                AnnotationValue value = modelNameAnnotation.value();
                if (value != null && !value.asString().isEmpty()) {
                    return value.asString();
                }
            }
        }
        return null;
    }

    private static void collectAgentsWithMethodAnnotations(IndexView index, DotName annotation,
            Map<ClassInfo, List<MethodInfo>> ifaceToAgentMethodsMap,
            Map<ClassInfo, List<MethodInfo>> classToNonAiAgentMethodsMap) {
        Collection<AnnotationInstance> annotations = index.getAnnotations(annotation);
        for (AnnotationInstance ai : annotations) {
            if (ai.target().kind() != AnnotationTarget.Kind.METHOD) {
                continue;
            }
            MethodInfo methodInfo = ai.target().asMethod();
            if (methodInfo.declaringClass().isInterface()) {
                ClassInfo iface = methodInfo.declaringClass();
                addMethodToMap(methodInfo, iface, ifaceToAgentMethodsMap);
                index.getAllKnownSubinterfaces(iface.name())
                        .forEach(i -> addMethodToMap(methodInfo, i, ifaceToAgentMethodsMap));
            } else {
                addMethodToMap(methodInfo, methodInfo.declaringClass(), classToNonAiAgentMethodsMap);
            }
        }
    }

    private static void addMethodToMap(MethodInfo methodInfo, ClassInfo iface, Map<ClassInfo, List<MethodInfo>> map) {
        map.computeIfAbsent(iface, (k) -> new ArrayList<>()).add(methodInfo);
    }

    @BuildStep
    public OutputKeyBuildItem outputKeyItems(DetectedAiAgentAsMapBuildItem detectedAiAgentAsMapBuildItem,
            DetectedNonAiAgentAsMapBuildItem detectedNonAiAgentAsMapBuildItem,
            CombinedIndexBuildItem indexBuildItem,
            BeanDiscoveryFinishedBuildItem beanDiscoveryFinished) {
        Map<DotName, List<MethodInfo>> ifaceToAgentMethodsMap = detectedAiAgentAsMapBuildItem.getIfaceToAgentMethodsMap();
        Map<DotName, List<MethodInfo>> classToNonAiAgentMethodsMap = detectedNonAiAgentAsMapBuildItem
                .getClassToNonAiAgentMethodsMap();
        IndexView index = indexBuildItem.getIndex();

        OutputKeyBuildItem.Builder builder = OutputKeyBuildItem.of();
        for (var entry : ifaceToAgentMethodsMap.entrySet()) {
            for (MethodInfo agenticMethod : entry.getValue()) {
                // handle any agent-related annotation that has the outputKey property specified
                for (DotName annotation : AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS) {
                    AnnotationInstance agentInstance = agenticMethod.annotation(annotation);
                    if (agentInstance == null) {
                        continue;
                    }

                    AnnotationInstance parallelMapperAnnotation = agenticMethod
                            .annotation(AgenticLangChain4jDotNames.PARALLEL_MAPPER_AGENT);
                    if (parallelMapperAnnotation != null) {
                        AnnotationValue subAgentsValue = parallelMapperAnnotation.value("subAgent");
                        if (subAgentsValue != null) {
                            // The first argument of the subagent method of a ParallelMapperAgent is implicitly passed by the mapper
                            subagentMethod(subAgentsValue.asClass().name(), ifaceToAgentMethodsMap, classToNonAiAgentMethodsMap)
                                    .map(subAgentMethod -> ValidationUtil.nonCdiParameters(subAgentMethod, index))
                                    .filter(parameters -> !parameters.isEmpty())
                                    .ifPresent(parameters -> {
                                        String parameterName = determineParameterName(parameters.get(0));
                                        builder.addUserProvidedKey(parameterName);
                                    });
                        }
                    }

                    AnnotationValue outputKeyValue = agentInstance.value("outputKey");
                    if (outputKeyValue != null) {
                        builder.addKeyType(outputKeyValue.asString(),
                                determineAgentMethodReturnType(agenticMethod.returnType()));
                    }
                }
                // handle subAgents attribute
                for (DotName annotation : AgenticLangChain4jDotNames.AGENT_ANNOTATIONS_WITH_SUB_AGENTS) {
                    AnnotationInstance agentInstance = agenticMethod.annotation(annotation);
                    if (agentInstance == null) {
                        continue;
                    }
                    AnnotationValue subAgentsValue = agentInstance.value("subAgents");
                    if (subAgentsValue == null) {
                        continue;
                    }
                    for (Type subAgentType : subAgentsValue.asClassArray()) {
                        // in order to determine the type associated with the outputKey, we need to look up
                        // the interface and check the return key and type of (the single agent-related) method

                        subagentMethod(subAgentType.name(), ifaceToAgentMethodsMap, classToNonAiAgentMethodsMap)
                                .ifPresent(subAgentMethod -> AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS.stream()
                                        .map(subAgentMethod::annotation)
                                        .filter(Objects::nonNull)
                                        .map(ann -> ann.value("outputKey"))
                                        .filter(Objects::nonNull)
                                        .map(AnnotationValue::asString)
                                        .findFirst()
                                        .ifPresent(outputKeyString -> builder.addKeyType(outputKeyString,
                                                determineAgentMethodReturnType(subAgentMethod.returnType()))));
                    }
                }
            }
        }

        // we need to go through the supervisor agents and mark the method parameters of all agents that are part
        // of the supervisor agent system
        index.getAnnotations(AgenticLangChain4jDotNames.SUPERVISOR_AGENT).forEach(instance -> {
            AnnotationValue subAgentsValue = instance.value("subAgents");
            if (subAgentsValue == null) {
                return;
            }
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                return;
            }
            MethodInfo supervisorAgentMethod = instance.target().asMethod();
            supervisorAgentMethod.parameters().forEach(pi -> {
                String parameterName = determineParameterName(pi);
                builder.addSupervisedKeys(parameterName);
            });
            for (Type subAgentType : subAgentsValue.asClassArray()) {
                ifaceToAgentMethodsMap.getOrDefault(subAgentType.name(), Collections.emptyList()).forEach(mi -> {
                    List<MethodParameterInfo> parameters = ValidationUtil.nonCdiParameters(mi, index);
                    parameters.forEach(pi -> {
                        String parameterName = determineParameterName(pi);
                        builder.addSupervisedKeys(parameterName);
                    });
                });
            }
        });

        // we need to know if any of the parameters are passed by the user, so we need to analyze the known usages
        beanDiscoveryFinished.getInjectionPoints().forEach(ip -> {
            DotName requiredName = ip.getRequiredType().name();
            List<MethodInfo> agentMethods = ifaceToAgentMethodsMap.getOrDefault(requiredName, Collections.emptyList());
            agentMethods.forEach(mi -> {
                List<MethodParameterInfo> parameters = ValidationUtil.nonCdiParameters(mi, index);
                parameters.forEach(pi -> {
                    String parameterName = determineParameterName(pi);
                    builder.addUserProvidedKey(parameterName);
                });
            });
        });

        return builder.build();
    }

    private Optional<MethodInfo> subagentMethod(DotName subagentName, Map<DotName, List<MethodInfo>> ifaceToAgentMethodsMap,
            Map<DotName, List<MethodInfo>> classToNonAiAgentMethodsMap) {
        List<MethodInfo> subAgentMethods = ifaceToAgentMethodsMap.get(subagentName);
        if (subAgentMethods == null) {
            subAgentMethods = classToNonAiAgentMethodsMap.get(subagentName);
        }
        if (subAgentMethods == null || subAgentMethods.isEmpty()) {
            throw new IllegalConfigurationException(
                    "Class '%s' is declared as a sub-agent but does not declare any agent method".formatted(subagentName));
        }
        if (subAgentMethods.size() != 1) {
            log.warn("Unable to determine agentic method for subagent with type '%s'"
                    .formatted(subagentName));
            return Optional.empty();
        }
        return Optional.of(subAgentMethods.get(0));
    }

    private DotName determineAgentMethodReturnType(Type type) {
        if (AgenticLangChain4jDotNames.RESULT_WITH_AGENTIC_SCOPE.equals(type.name())) {
            if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                return type.asParameterizedType().arguments().get(0).name();
            } else {
                return DotNames.OBJECT;
            }
        }
        return type.name();
    }

    /**
     * The idea here is to check parameters used in agentic methods and if the name matches an output key, the type must match
     */
    @BuildStep
    @Produce(ServiceStartBuildItem.class)
    public void validateAgenticParameterTypes(CombinedIndexBuildItem indexBuildItem, OutputKeyBuildItem outputKeyBuildItem) {
        List<DotName> annotationsToCheck = new ArrayList<>(AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS.size() + 2);
        annotationsToCheck.addAll(AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS);
        annotationsToCheck.add(AgenticLangChain4jDotNames.OUTPUT);
        annotationsToCheck.add(AgenticLangChain4jDotNames.EXIT_CONDITION);

        IndexView index = indexBuildItem.getIndex();

        annotationsToCheck.forEach(annotation -> {
            index.getAnnotations(annotation).forEach(instance -> {
                if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                    return;
                }
                MethodInfo method = instance.target().asMethod();
                // don't validate any of the upstream agents
                if (method.declaringClass().name().toString().startsWith(AGENTIC_PACKAGE_PREFIX)) {
                    return;
                }
                List<MethodParameterInfo> parameters = method.parameters();
                for (int i = 0; i < parameters.size(); i++) {
                    MethodParameterInfo parameter = parameters.get(i);
                    // resolved from CDI, not from the AgenticScope
                    if (ValidationUtil.isCdiResolvable(parameter, index)) {
                        continue;
                    }
                    String parameterName = determineParameterName(parameter);
                    if ((parameterName == null)) {
                        continue;
                    }
                    Type parameterType = parameter.type();
                    if (AgenticLangChain4jDotNames.AGENTIC_SCOPE.equals(parameterType.name())) {
                        continue;
                    }
                    // like the AgenticScope, langchain4j-agentic supplies it itself (from the scope's execution context),
                    // so no agent has to provide it as an output key
                    if (AgenticLangChain4jDotNames.INVOCATION_PARAMETERS.equals(parameterType.name())) {
                        continue;
                    }
                    if (parameter.annotation(AgenticLangChain4jDotNames.MEMORY_ID) != null) {
                        continue;
                    }
                    DotName expectedParameterTypeName = outputKeyBuildItem.getKeyToTypeMap().get(parameterName);
                    if (expectedParameterTypeName == null) {
                        if (needsResolution(outputKeyBuildItem, parameterName)) {
                            throw new IllegalConfigurationException(
                                    "No agent provides an output key named '%s'. This means that parameter no.%d of method '%s' of class '%s' cannot be resolved"
                                            .formatted(
                                                    parameterName, i, method.declaringClass().name(),
                                                    method.declaringClass().name()));
                        }
                    } else if (!expectedParameterTypeName.equals(parameterType.name())) {
                        if (isParallelMapperOutput(method.declaringClass(), parameterName)) {
                            // allow ParallelMapperAgent to transform the type of its output
                            continue;
                        }
                        throw new IllegalConfigurationException(
                                "Parameter no.%d of method '%s' of class '%s' was expected to be of type '%s'".formatted(i,
                                        method.name(), method.declaringClass().name(), expectedParameterTypeName));
                    }
                }
            });
        });
    }

    @BuildStep
    @Produce(ServiceStartBuildItem.class)
    public void validateAgentNames(DetectedAiAgentAsMapBuildItem detectedAiAgentAsMapBuildItem,
            DetectedNonAiAgentAsMapBuildItem detectedNonAiAgentAsMapBuildItem) {
        Map<DotName, List<MethodInfo>> ifaceToAgentMethodsMap = detectedAiAgentAsMapBuildItem.getIfaceToAgentMethodsMap();
        Map<DotName, List<MethodInfo>> classToNonAiAgentMethodsMap = detectedNonAiAgentAsMapBuildItem
                .getClassToNonAiAgentMethodsMap();

        for (var entry : ifaceToAgentMethodsMap.entrySet()) {
            if (entry.getKey().toString().startsWith(AGENTIC_PACKAGE_PREFIX)) {
                continue;
            }
            for (MethodInfo agenticMethod : entry.getValue()) {
                for (DotName annotation : AgenticLangChain4jDotNames.AGENT_ANNOTATIONS_WITH_SUB_AGENTS) {
                    AnnotationInstance instance = agenticMethod.annotation(annotation);
                    if (instance == null) {
                        continue;
                    }
                    validateSubAgentNames(instance, entry.getKey(), ifaceToAgentMethodsMap, classToNonAiAgentMethodsMap);
                }
            }
        }
    }

    /**
     * Explicitly assigned duplicate agent names are rejected. Implicit duplicates (method names) are legal at
     * runtime, but under a supervisor they reach the planner prompt distinguished only by a positional suffix,
     * so those get a warning.
     */
    private void validateSubAgentNames(AnnotationInstance workflowInstance, DotName rootAgentName,
            Map<DotName, List<MethodInfo>> ifaceToAgentMethodsMap,
            Map<DotName, List<MethodInfo>> classToNonAiAgentMethodsMap) {
        boolean isSupervisor = AgenticLangChain4jDotNames.SUPERVISOR_AGENT.equals(workflowInstance.name());
        Map<String, DotName> explicitNameToClass = new HashMap<>();
        Map<String, DotName> effectiveNameToClass = new HashMap<>();

        List<Type> subAgentTypes = new ArrayList<>();
        AnnotationValue subAgentsValue = workflowInstance.value("subAgents");
        if (subAgentsValue != null) {
            subAgentTypes.addAll(List.of(subAgentsValue.asClassArray()));
        }
        AnnotationValue subAgentValue = workflowInstance.value("subAgent");
        if (subAgentValue != null) {
            subAgentTypes.add(subAgentValue.asClass());
        }

        for (Type subAgentType : subAgentTypes) {
            DotName subAgentClassName = subAgentType.name();
            Optional<MethodInfo> maybeSubAgentMethod = subagentMethod(subAgentClassName, ifaceToAgentMethodsMap,
                    classToNonAiAgentMethodsMap);
            if (maybeSubAgentMethod.isEmpty()) {
                continue;
            }
            MethodInfo subAgentMethod = maybeSubAgentMethod.get();
            for (DotName annotation : AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS) {
                AnnotationInstance subAgentInstance = subAgentMethod.annotation(annotation);
                if (subAgentInstance == null) {
                    continue;
                }
                String explicitName = explicitAgentName(subAgentInstance);
                if (explicitName != null) {
                    DotName previous = explicitNameToClass.put(explicitName, subAgentClassName);
                    if (previous != null && !previous.equals(subAgentClassName)) {
                        throw new IllegalConfigurationException(
                                "Duplicate agent name '%s' used by both '%s' and '%s' in the agentic system rooted at '%s'"
                                        .formatted(explicitName, previous, subAgentClassName, rootAgentName));
                    }
                }
                if (isSupervisor) {
                    String effectiveName = explicitName != null ? explicitName : subAgentMethod.name();
                    DotName previous = effectiveNameToClass.put(effectiveName, subAgentClassName);
                    if (previous != null && !previous.equals(subAgentClassName)) {
                        log.warn(
                                "Sub-agents '%s' and '%s' of supervisor agent '%s' share the agent name '%s'. The supervisor planner distinguishes them only by a positional suffix, which can degrade planning"
                                        .formatted(previous, subAgentClassName, rootAgentName, effectiveName));
                    }
                }
                validateSubAgentNames(subAgentInstance, rootAgentName, ifaceToAgentMethodsMap,
                        classToNonAiAgentMethodsMap);
                break;
            }
        }
    }

    private static String explicitAgentName(AnnotationInstance agentInstance) {
        AnnotationValue nameValue = agentInstance.value("name");
        if (nameValue != null && !nameValue.asString().isBlank()) {
            return nameValue.asString();
        }
        return null;
    }

    @BuildStep
    @Produce(ServiceStartBuildItem.class)
    public void warnAboutSupervisorSubAgentsWithoutDescription(CombinedIndexBuildItem indexBuildItem,
            DetectedAiAgentAsMapBuildItem detectedAiAgentAsMapBuildItem,
            DetectedNonAiAgentAsMapBuildItem detectedNonAiAgentAsMapBuildItem) {
        Map<DotName, List<MethodInfo>> ifaceToAgentMethodsMap = detectedAiAgentAsMapBuildItem.getIfaceToAgentMethodsMap();
        Map<DotName, List<MethodInfo>> classToNonAiAgentMethodsMap = detectedNonAiAgentAsMapBuildItem
                .getClassToNonAiAgentMethodsMap();

        indexBuildItem.getIndex().getAnnotations(AgenticLangChain4jDotNames.SUPERVISOR_AGENT).forEach(instance -> {
            if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                return;
            }
            MethodInfo supervisorMethod = instance.target().asMethod();
            if (supervisorMethod.declaringClass().name().toString().startsWith(AGENTIC_PACKAGE_PREFIX)) {
                return;
            }
            AnnotationValue subAgentsValue = instance.value("subAgents");
            if (subAgentsValue == null) {
                return;
            }
            for (Type subAgentType : subAgentsValue.asClassArray()) {
                subagentMethod(subAgentType.name(), ifaceToAgentMethodsMap, classToNonAiAgentMethodsMap)
                        .ifPresent(subAgentMethod -> {
                            if (!hasDescription(subAgentMethod)) {
                                log.warn(
                                        "Sub-agent '%s' of supervisor agent '%s' has no description. The supervisor planner relies on agent descriptions to decide which agent to invoke"
                                                .formatted(subAgentType.name(),
                                                        supervisorMethod.declaringClass().name()));
                            }
                        });
            }
        });
    }

    private static boolean hasDescription(MethodInfo agentMethod) {
        for (DotName annotation : AgenticLangChain4jDotNames.ALL_AGENT_ANNOTATIONS) {
            AnnotationInstance agentInstance = agentMethod.annotation(annotation);
            if (agentInstance == null) {
                continue;
            }
            AnnotationValue descriptionValue = agentInstance.value("description");
            if (descriptionValue != null && !descriptionValue.asString().isBlank()) {
                return true;
            }
            AnnotationValue aliasValue = agentInstance.value("value");
            return aliasValue != null && !aliasValue.asString().isBlank();
        }
        return false;
    }

    private static boolean needsResolution(OutputKeyBuildItem outputKeyBuildItem, String parameterName) {
        return !outputKeyBuildItem.getUserProvidedKeys().contains(parameterName)
                && !outputKeyBuildItem.getSupervisedKeys().contains(parameterName);
    }

    private static boolean isParallelMapperOutput(ClassInfo agentClass, String parameterName) {
        return agentClass.methods().stream().filter(m -> m.annotation(ParallelMapperAgent.class) != null)
                .findFirst()
                .flatMap(m -> Optional.ofNullable(m.annotation(ParallelMapperAgent.class).value("outputKey")))
                .map(AnnotationValue::asString)
                .map(outputKey -> outputKey.equals(parameterName))
                .orElse(false);
    }

    private String determineParameterName(MethodParameterInfo parameter) {
        String name = parameter.name();
        AnnotationInstance vInstance = parameter.annotation(LangChain4jDotNames.V);
        if (vInstance != null) {
            name = vInstance.value().asString();
        }
        return name;
    }

}
