package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.deployment.DotNames.COMPLETION_STAGE;
import static io.quarkiverse.langchain4j.deployment.DotNames.MULTI;
import static io.quarkiverse.langchain4j.deployment.DotNames.UNI;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.a2a.server.runtime.A2AEventBusStarter;
import io.quarkiverse.langchain4j.a2a.server.runtime.A2AServerRecorder;
import io.quarkiverse.langchain4j.a2a.server.runtime.authorization.DenyingTaskAuthorizationProvider;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.AgentCardProducer;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.ConfigCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.DetectedTransports;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.ExposedAgent;
import io.quarkiverse.langchain4j.a2a.server.runtime.executor.QuarkusBaseAgentExecutor;
import io.quarkiverse.langchain4j.deployment.AiServicesUtil;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.BeanResolver;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

public class A2AServerProcessor {

    private static final String FEATURE = "langchain4j-a2a-server";

    private static final String TEXT_PLAIN = "text/plain";
    private static final String APPLICATION_JSON = "application/json";

    private static final DotName USER_MESSAGE = DotName.createSimple(UserMessage.class);
    private static final DotName PUBLIC_AGENT_CARD = DotName.createSimple(PublicAgentCard.class);

    private static final DotName MEMORY_ID = DotName.createSimple(MemoryId.class);

    /**
     * Return types that hand the answer back later or piece by piece, or wrap it with metadata. The executor sends
     * what the method returns as the answer, so these would reach the client as the JSON form of the wrapper.
     */
    private static final Set<DotName> DEFERRED_RESULT_TYPES = Set.of(
            MULTI,
            UNI,
            COMPLETION_STAGE,
            DotName.createSimple(CompletableFuture.class),
            DotName.createSimple(Future.class),
            LangChain4jDotNames.TOKEN_STREAM,
            LangChain4jDotNames.RESULT);

    private static final String EVENT_BUS_INITIALIZER = "org.a2aproject.sdk.server.events.MainEventBusProcessorInitializer";

    /**
     * Marker class of each reference implementation, mapped to the transport it serves. Iteration order determines
     * the order of {@code supportedInterfaces} on the Agent Card, whose first entry is the preferred one.
     */
    private static final Map<String, TransportProtocol> REFERENCE_IMPL_TO_TRANSPORT = new LinkedHashMap<>();

    static {
        REFERENCE_IMPL_TO_TRANSPORT.put("org.a2aproject.sdk.server.apps.quarkus.A2AServerRoutes",
                TransportProtocol.JSONRPC);
    }

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * With no agent to expose there is no card to build and no task to authorize, so the beans providing them are
     * vetoed.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void beans(CombinedIndexBuildItem indexBuildItem,
            A2AServerRecorder recorder,
            BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformerProducer,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            BuildProducer<GeneratedBeanBuildItem> generatedBeanProducer) {

        IndexView index = indexBuildItem.getIndex();
        Collection<AnnotationInstance> exposeInstances = index.getAnnotations(ExposeA2AAgent.class);
        if (exposeInstances.isEmpty()) {
            annotationsTransformerProducer.produce(new AnnotationsTransformerBuildItem(vetoingClassTransformation(
                    Set.of(AgentCardProducer.class.getName(), ConfigCardBuilderCustomizer.class.getName(),
                            DenyingTaskAuthorizationProvider.class.getName()))));
            return;
        }
        validateSingleAgent(exposeInstances);

        createDetectedTransportsBean(recorder, syntheticBeanProducer);
        ClassOutput generatedBeanOutput = new GeneratedBeanGizmoAdaptor(generatedBeanProducer);
        for (AnnotationInstance exposeInstance : exposeInstances) {
            ClassInfo targetClass = exposeInstance.target().asClass();
            MethodInfo aiServiceMethod = determineExposedMethod(targetClass, index);

            createAgentCardBean(recorder, syntheticBeanProducer, exposeInstance, aiServiceMethod);
            generateAgentExecutor(targetClass, aiServiceMethod, generatedBeanOutput);
        }
    }

    /**
     * The SDK serves every request with the one agent executor and the one public card there is, so a second agent
     * would leave both ambiguous.
     */
    private static void validateSingleAgent(Collection<AnnotationInstance> exposeInstances) {
        DotName first = null;
        for (AnnotationInstance exposeInstance : exposeInstances) {
            DotName agent = exposeInstance.target().asClass().name();
            if (first == null) {
                first = agent;
                continue;
            }
            throw new DeploymentException("Both '" + first + "' and '" + agent + "' are annotated with "
                    + "'@ExposeA2AAgent', but an application can only expose one agent");
        }
    }

    /**
     * An A2A agent is backed by an AI Service, a CDI bean exposing its contract through a single interface method,
     * which is what the generated executor calls.
     */
    private MethodInfo determineExposedMethod(ClassInfo classInfo, IndexView index) {
        if (!classInfo.isInterface()) {
            throw new DeploymentException("'@ExposeA2AAgent' can only be placed on an interface. Offending class is '"
                    + classInfo.name() + "'");
        }
        if (!classInfo.hasDeclaredAnnotation(RegisterAiService.class)) {
            throw new DeploymentException("'@ExposeA2AAgent' can only be placed on an AI Service annotated with "
                    + "'@RegisterAiService'. Offending class is '" + classInfo.name() + "'");
        }
        List<MethodInfo> candidates = AiServicesUtil.determineAiServiceMethods(classInfo, index);
        if (candidates.size() != 1) {
            throw new DeploymentException("'@ExposeA2AAgent' can only be placed on a type that has a single method. "
                    + "Offending class is '" + classInfo.name() + "'");
        }
        MethodInfo exposedMethod = candidates.get(0);
        if (exposedMethod.returnType().kind() == Type.Kind.VOID) {
            throw new DeploymentException("'@ExposeA2AAgent' requires a method that returns the agent's answer, and '"
                    + classInfo.name() + "#" + exposedMethod.name() + "' returns void");
        }
        if (DEFERRED_RESULT_TYPES.contains(exposedMethod.returnType().name())) {
            throw new DeploymentException("'@ExposeA2AAgent' requires a method that returns the agent's answer "
                    + "itself, and '" + classInfo.name() + "#" + exposedMethod.name() + "' returns "
                    + exposedMethod.returnType().name().withoutPackagePrefix());
        }
        if (!DotNames.STRING.equals(exposedMethod.returnType().name())) {
            throw new DeploymentException("'@ExposeA2AAgent' requires a method that returns the agent's answer as a "
                    + "String, and '" + classInfo.name() + "#" + exposedMethod.name() + "' returns "
                    + exposedMethod.returnType().name().withoutPackagePrefix());
        }
        return exposedMethod;
    }

    /**
     * Each A2A transport is served by its own reference implementation, which registers its routes as soon as it is
     * on the classpath. Only the ones actually present may be advertised on the Agent Card, otherwise clients are
     * pointed at endpoints that do not answer.
     */
    private void createDetectedTransportsBean(A2AServerRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer) {
        List<String> protocolBindings = new ArrayList<>();
        for (Map.Entry<String, TransportProtocol> entry : REFERENCE_IMPL_TO_TRANSPORT.entrySet()) {
            if (QuarkusClassLoader.isClassPresentAtRuntime(entry.getKey())) {
                protocolBindings.add(entry.getValue().asString());
            }
        }
        if (protocolBindings.isEmpty()) {
            throw new DeploymentException(
                    "No A2A transport was found on the classpath. Add one of the A2A reference implementations, "
                            + "for example 'org.a2aproject.sdk:a2a-java-sdk-reference-jsonrpc'");
        }

        syntheticBeanProducer.produce(SyntheticBeanBuildItem
                .configure(DetectedTransports.class)
                .setRuntimeInit()
                .runtimeValue(recorder.detectedTransports(protocolBindings))
                .done());
    }

    /**
     * The SDK reports a denied task operation as {@code TaskNotFoundError}, and serves every task to every caller
     * once the check is off, neither of which the application would otherwise hear about.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void reportTaskAuthorization(CombinedIndexBuildItem indexBuildItem,
            A2AServerRecorder recorder,
            BeanContainerBuildItem beanContainerBuildItem) {
        if (indexBuildItem.getIndex().getAnnotations(ExposeA2AAgent.class).isEmpty()) {
            return;
        }
        recorder.reportTaskAuthorization();
    }

    /**
     * The SDK looks the provider up through an unqualified {@code Instance} and uses it only if it resolves, so two
     * providers of the application's own would both be ignored, without a word.
     */
    @BuildStep
    void rejectAmbiguousTaskAuthorizationProviders(CombinedIndexBuildItem indexBuildItem,
            ValidationPhaseBuildItem validationPhaseBuildItem,
            BuildProducer<ValidationErrorBuildItem> validationErrorProducer) {
        if (indexBuildItem.getIndex().getAnnotations(ExposeA2AAgent.class).isEmpty()) {
            return;
        }
        BeanResolver beanResolver = validationPhaseBuildItem.getBeanResolver();
        Set<BeanInfo> providers = beanResolver.resolveBeans(ClassType.create(TaskAuthorizationProvider.class),
                AnnotationInstance.builder(DotNames.DEFAULT).build());
        try {
            beanResolver.resolveAmbiguity(providers);
        } catch (AmbiguousResolutionException e) {
            List<String> providerClasses = new ArrayList<>();
            for (BeanInfo provider : providers) {
                if (!provider.isDefaultBean()) {
                    providerClasses.add(provider.getBeanClass().toString());
                }
            }
            validationErrorProducer.produce(new ValidationErrorBuildItem(new DeploymentException(
                    "There is more than one TaskAuthorizationProvider bean, " + providerClasses
                            + ", so the A2A Java SDK would use none of them. Keep one")));
        }
    }

    /**
     * The SDK's {@code MainEventBusProcessorInitializer} observes {@code @Initialized(ApplicationScoped.class)},
     * which Quarkus fires during static initialization. {@link A2AEventBusStarter} replaces it, starting the
     * processor at runtime instead.
     *
     * @see <a href="https://github.com/a2aproject/a2a-java/issues/1032">a2a-java#1032: no native-image
     *      reachability metadata</a>
     */
    @BuildStep
    void vetoEagerEventBusInitializer(BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformerProducer) {
        annotationsTransformerProducer.produce(new AnnotationsTransformerBuildItem(
                vetoingClassTransformation(Set.of(EVENT_BUS_INITIALIZER))));
    }

    /**
     * Identifies which parameter carries the text the client sent, following the same rules an AI Service uses:
     * an explicit {@code @UserMessage} wins, otherwise the only parameter that is not a memory id qualifies.
     */
    private MethodParameterInfo determineUserMessageParameter(MethodInfo aiServiceMethod) {
        List<MethodParameterInfo> candidates = new ArrayList<>();
        for (MethodParameterInfo parameter : aiServiceMethod.parameters()) {
            if (parameter.hasAnnotation(USER_MESSAGE)) {
                return parameter;
            }
            if (!parameter.hasAnnotation(MEMORY_ID)) {
                candidates.add(parameter);
            }
        }
        if (candidates.isEmpty()) {
            throw new DeploymentException("'" + aiServiceMethod.declaringClass().name() + "#" + aiServiceMethod.name()
                    + "' needs a String parameter to receive the message sent by the A2A client");
        }
        if (candidates.size() != 1) {
            throw new DeploymentException("Unable to tell which parameter of '" + aiServiceMethod.declaringClass().name()
                    + "#" + aiServiceMethod.name() + "' carries the message sent by the A2A client. Annotate it with "
                    + "'@UserMessage'");
        }
        return candidates.get(0);
    }

    /**
     * Maps a parameter of the AI Service method onto the part of the A2A request that feeds it. Only one source
     * exists: the text the client sent.
     */
    private ResultHandle loadParameter(MethodCreator invoke, MethodParameterInfo parameter,
            MethodParameterInfo userMessageParam) {
        if (!DotNames.STRING.equals(parameter.type().name())) {
            throw new DeploymentException("Parameter '" + parameterLabel(parameter) + "' is of type '"
                    + parameter.type().name() + "', but an A2A request can only supply Strings");
        }
        if (parameter.position() == userMessageParam.position()) {
            return invoke.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(RequestContext.class, "getUserInput", String.class, String.class),
                    invoke.getMethodParam(0), invoke.load("\n"));
        }
        throw new DeploymentException("Parameter '" + parameterLabel(parameter) + "' cannot be filled from an A2A "
                + "request. Only the message itself is available");
    }

    private String parameterLabel(MethodParameterInfo parameter) {
        String name = parameter.name();
        return (name != null ? name : "#" + parameter.position()) + "' of '"
                + parameter.method().declaringClass().name() + "#" + parameter.method().name();
    }

    /**
     * The Agent Card declares what an agent accepts and returns as media types, so the Java types of the AI Service
     * method have to be mapped onto one. Everything the extension does not exchange as plain text is exchanged as
     * JSON.
     */
    private static String mediaTypeOf(Type type) {
        return DotNames.STRING.equals(type.name()) ? TEXT_PLAIN : APPLICATION_JSON;
    }

    private AnnotationTransformation vetoingClassTransformation(final Set<String> classNames) {
        return new AnnotationTransformation() {
            @Override
            public boolean supports(AnnotationTarget.Kind kind) {
                return kind == AnnotationTarget.Kind.CLASS;
            }

            @Override
            public void apply(TransformationContext context) {
                if (classNames.contains(context.declaration().asClass().name().toString())) {
                    context.add(AnnotationInstance.builder(DotNames.VETOED).buildWithTarget(context.declaration()));
                }
            }
        };
    }

    /**
     * Only the parameter carrying the message describes what the agent accepts. The agent implements none of the
     * optional capabilities, so the card advertises none.
     */
    private void createAgentCardBean(A2AServerRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            AnnotationInstance exposeInstance,
            MethodInfo aiServiceMethod) {
        List<String> defaultInputModes = List.of(mediaTypeOf(determineUserMessageParameter(aiServiceMethod).type()));
        List<String> defaultOutputModes = List.of(mediaTypeOf(aiServiceMethod.returnType()));

        AnnotationInstance[] skillInstances = exposeInstance.value("skills").asNestedArray();
        List<AgentSkill> skills = new ArrayList<>();
        for (AnnotationInstance skillInstance : skillInstances) {
            AgentSkill.Builder skillBuilder = AgentSkill.builder();
            String skillId = skillInstance.value("id").asString();
            skillBuilder.id(skillId);
            skillBuilder.name(skillInstance.value("name").asString());
            skillBuilder.description(skillInstance.value("description").asString());
            AnnotationValue tagsValue = skillInstance.value("tags");
            String[] tags = tagsValue != null ? tagsValue.asStringArray() : null;
            if (tags == null || tags.length == 0) {
                throw new DeploymentException("Skill '" + skillId + "' declares no tag, but the A2A specification "
                        + "requires every skill to carry at least one");
            }
            skillBuilder.tags(Arrays.asList(tags));
            AnnotationValue examplesValue = skillInstance.value("examples");
            if (examplesValue != null) {
                skillBuilder.examples(Arrays.asList(examplesValue.asStringArray()));
            }
            skills.add(skillBuilder.build());
        }

        ExposedAgent agent = new ExposedAgent(
                new AgentCapabilities(false, false, false, Collections.emptyList()),
                defaultInputModes, defaultOutputModes, skills);

        SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                .configure(AgentCard.class)
                .scope(Dependent.class)
                .addInjectionPoint(ClassType.create(AgentCardProducer.class))
                .createWith(recorder.agentCard(agent))
                .setRuntimeInit()
                .addQualifier(PUBLIC_AGENT_CARD)
                .unremovable();
        syntheticBeanProducer.produce(configurator.done());
    }

    /**
     * Generates an implementation of {@link AgentExecutor} that looks something like:
     *
     * <pre>
     * &#64;Singleton
     * public class WeatherAgent$AgentExecutor extends QuarkusBaseAgentExecutor {
     *
     *     private final WeatherAgent aiService;
     *
     *     &#64;Inject
     *     public WeatherAgent$AgentExecutor(WeatherAgent aiService) {
     *         this.aiService = aiService;
     *     }
     *
     *     protected List<Part<?>> invoke(RequestContext context) {
     *         String aiServiceResult = aiService.chat(context.getUserInput("\n"));
     *         return stringResultToParts(aiServiceResult);
     *     }
     * }
     * </pre>
     * <p>
     * The {@code String} result travels as a {@code TextPart}, matching the output mode the Agent Card advertises.
     * The class is named after the full binary name of the target, so member interfaces with the same simple name do
     * not collide.
     */
    private void generateAgentExecutor(ClassInfo classInfo, MethodInfo aiServiceMethod, ClassOutput classOutput) {
        String implClassName = classInfo.name().toString() + "$AgentExecutor";
        ClassCreator.Builder classCreatorBuilder = ClassCreator.builder()
                .classOutput(classOutput)
                .className(implClassName)
                .superClass(QuarkusBaseAgentExecutor.class);
        try (ClassCreator classCreator = classCreatorBuilder.build()) {
            classCreator.addAnnotation(Singleton.class);

            FieldDescriptor aiServiceField = classCreator.getFieldCreator("aiService", classInfo.name().toString())
                    .setModifiers(Modifier.PRIVATE | Modifier.FINAL)
                    .getFieldDescriptor();
            {
                MethodCreator ctor = classCreator.getMethodCreator(MethodDescriptor.INIT, "V",
                        classInfo.name().toString());
                ctor.setModifiers(Modifier.PUBLIC);
                ctor.addAnnotation(Inject.class);
                ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(QuarkusBaseAgentExecutor.class),
                        ctor.getThis());
                ctor.writeInstanceField(aiServiceField, ctor.getThis(),
                        ctor.getMethodParam(0));
                ctor.returnValue(null);
            }

            {
                MethodCreator invoke = classCreator
                        .getMethodCreator(MethodDescriptor.ofMethod(implClassName, "invoke", List.class,
                                RequestContext.class));
                invoke.setModifiers(Modifier.PROTECTED);

                List<MethodParameterInfo> aiServiceMethodParams = aiServiceMethod.parameters();
                MethodParameterInfo userMessageParam = determineUserMessageParameter(aiServiceMethod);

                List<ResultHandle> aiServiceMethodParamHandles = new ArrayList<>();
                for (MethodParameterInfo parameter : aiServiceMethodParams) {
                    aiServiceMethodParamHandles.add(loadParameter(invoke, parameter, userMessageParam));
                }

                ResultHandle aiServiceResultHandle = invoke.invokeInterfaceMethod(MethodDescriptor.of(aiServiceMethod),
                        invoke.readInstanceField(aiServiceField, invoke.getThis()),
                        aiServiceMethodParamHandles.toArray(new ResultHandle[aiServiceMethodParams.size()]));

                ResultHandle result = invoke.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(implClassName, "stringResultToParts", List.class, String.class),
                        invoke.getThis(), aiServiceResultHandle);

                invoke.returnValue(result);
            }
        }
    }
}
