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
import java.util.stream.Stream;

import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentExtension;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.a2a.server.runtime.A2AEventBusStarter;
import io.quarkiverse.langchain4j.a2a.server.runtime.A2AServerRecorder;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.AgentCardProducer;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.ConfigCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.DetectedTransports;
import io.quarkiverse.langchain4j.a2a.server.runtime.executor.QuarkusBaseAgentExecutor;
import io.quarkiverse.langchain4j.deployment.AiServicesUtil;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
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
    private static final DotName MEMORY_ID = DotName.createSimple(MemoryId.class);

    /**
     * Declarative workflow annotations of the agentic module, referenced by name so that this extension keeps
     * working when that module is not on the classpath. Mirrors
     * {@code AgenticLangChain4jDotNames.AGENT_ANNOTATIONS_WITH_SUB_AGENTS}, which is the set of annotations that
     * declare a workflow rather than a single agent.
     */
    private static final String DECLARATIVE_PACKAGE = "dev.langchain4j.agentic.declarative.";
    private static final Set<DotName> AGENTIC_WORKFLOW_ANNOTATIONS = Set.of(
            DotName.createSimple(DECLARATIVE_PACKAGE + "SequenceAgent"),
            DotName.createSimple(DECLARATIVE_PACKAGE + "ParallelAgent"),
            DotName.createSimple(DECLARATIVE_PACKAGE + "ParallelMapperAgent"),
            DotName.createSimple(DECLARATIVE_PACKAGE + "LoopAgent"),
            DotName.createSimple(DECLARATIVE_PACKAGE + "ConditionalAgent"),
            DotName.createSimple(DECLARATIVE_PACKAGE + "SupervisorAgent"),
            DotName.createSimple(DECLARATIVE_PACKAGE + "PlannerAgent"));

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

    private static final DotName RESULT_WITH_AGENTIC_SCOPE = DotName
            .createSimple("dev.langchain4j.agentic.scope.ResultWithAgenticScope");

    /** The domain types the protocol is expressed in, plus the {@code ErrorDetail} an error response carries. */
    private static final List<DotName> PROTOCOL_TYPE_PACKAGES = List.of(
            DotName.createSimple("org.a2aproject.sdk.spec"),
            DotName.createSimple("org.a2aproject.sdk.spec.util"));

    /**
     * Packages searched for the protobuf messages a request is parsed into: those the SDK generates from the A2A
     * schema, and the well-known types of protobuf itself that they are built out of.
     * <p>
     * These are registered whatever the transport. The messages are the SDK's internal representation of a request,
     * not a detail of the gRPC binding, so a JSON-RPC-only server parses into them too — without them it answers
     * every call with {@code Generated message class "SendMessageRequest" missing method "getTenant"}.
     * <p>
     * The generated messages share a package with the gRPC service stubs, which extend {@code AbstractStub} and so
     * cannot even be loaded unless the gRPC transport is on the classpath. Only the messages are wanted, and what
     * tells them apart is that they derive from a protobuf type.
     */
    private static final List<DotName> PROTOBUF_MESSAGE_PACKAGES = List.of(
            DotName.createSimple("org.a2aproject.sdk.grpc"),
            DotName.createSimple("com.google.protobuf"));
    private static final String PROTOBUF_PACKAGE_PREFIX = "com.google.protobuf.";

    private static final String GRPC_REFERENCE_IMPL = "org.a2aproject.sdk.server.grpc.quarkus.QuarkusGrpcHandler";

    /**
     * The messages the gRPC binding builds an error response out of: a {@code google.rpc.Status} whose details
     * hold an {@code ErrorInfo}. They come from Google's common protos rather than from protobuf itself, so the
     * packages in {@link #PROTOBUF_MESSAGE_PACKAGES} do not reach them.
     */
    private static final String[] GRPC_ERROR_TYPES = {
            "com.google.rpc.Status",
            "com.google.rpc.Status$Builder",
            "com.google.rpc.ErrorInfo",
            "com.google.rpc.ErrorInfo$Builder" };

    private static final String DEFAULTS_RESOURCE = "META-INF/a2a-defaults.properties";
    private static final String EVENT_BUS_INITIALIZER = "org.a2aproject.sdk.server.events.MainEventBusProcessorInitializer";
    private static final String TRANSPORT_METADATA_SERVICE = "org.a2aproject.sdk.server.TransportMetadata";
    private static final String AUTHORIZATION_REQUIRED = "a2a.authorization.required";

    /**
     * Marker class of each reference implementation, mapped to the transport it serves. Iteration order determines
     * the order of {@code supportedInterfaces} on the Agent Card, whose first entry is the preferred one.
     */
    private static final Map<String, TransportProtocol> REFERENCE_IMPL_TO_TRANSPORT = new LinkedHashMap<>();

    static {
        REFERENCE_IMPL_TO_TRANSPORT.put("org.a2aproject.sdk.server.apps.quarkus.A2AServerRoutes",
                TransportProtocol.JSONRPC);
        REFERENCE_IMPL_TO_TRANSPORT.put("org.a2aproject.sdk.server.rest.quarkus.A2AServerRoutes",
                TransportProtocol.HTTP_JSON);
        REFERENCE_IMPL_TO_TRANSPORT.put(GRPC_REFERENCE_IMPL, TransportProtocol.GRPC);
    }

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void beans(CombinedIndexBuildItem indexBuildItem,
            A2AServerRecorder recorder,
            BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformerProducer,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            BuildProducer<GeneratedBeanBuildItem> generatedBeanProducer,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchyProducer) {

        IndexView index = indexBuildItem.getIndex();
        Collection<AnnotationInstance> exposeInstances = index.getAnnotations(ExposeA2AAgent.class);
        if (exposeInstances.isEmpty()) {

            // With no agent to expose there is no card to build, so the whole card machinery is vetoed
            annotationsTransformerProducer.produce(new AnnotationsTransformerBuildItem(vetoingClassTransformation(
                    Set.of(AgentCardProducer.class.getName(), ConfigCardBuilderCustomizer.class.getName()))));

            return;
        }
        if (exposeInstances.size() > 1) {
            throw new DeploymentException("Multiple expose instances found for '" + ExposeA2AAgent.class.getName()
                    + "'. Currently, only exposing a single A2A agent is supported");
        }
        AnnotationInstance exposeInstance = exposeInstances.iterator().next();
        ClassInfo targetClass = exposeInstance.target().asClass();
        MethodInfo aiServiceMethod = determineExposedMethod(targetClass, index);

        createAgentCardBean(recorder, syntheticBeanProducer, exposeInstance, aiServiceMethod);
        createDetectedTransportsBean(recorder, syntheticBeanProducer);
        if (!targetClass.hasDeclaredAnnotation(RegisterAiService.class)) {
            registerResultForReflection(aiServiceMethod, reflectiveHierarchyProducer);
        }
        ClassOutput generatedBeanOutput = new GeneratedBeanGizmoAdaptor(generatedBeanProducer);
        generateAgentExecutor(targetClass, aiServiceMethod, generatedBeanOutput);
    }

    /**
     * An A2A agent can be backed either by an AI Service or by a declarative workflow of the agentic module. Both
     * are CDI beans exposing their contract through a single interface method, which is what the generated
     * executor calls.
     */
    private MethodInfo determineExposedMethod(ClassInfo classInfo, IndexView index) {
        // the executor is generated around an interface call, and both kinds of target are interfaces
        if (!classInfo.isInterface()) {
            throw new DeploymentException("'@ExposeA2AAgent' can only be placed on an interface. Offending class is '"
                    + classInfo.name() + "'");
        }
        List<MethodInfo> candidates;
        if (classInfo.hasDeclaredAnnotation(RegisterAiService.class)) {
            candidates = AiServicesUtil.determineAiServiceMethods(classInfo, index);
        } else {
            candidates = agenticWorkflowMethods(classInfo);
            if (candidates.isEmpty()) {
                throw new DeploymentException("'@ExposeA2AAgent' can only be placed on an AI Service annotated with "
                        + "'@RegisterAiService', or on an agentic workflow interface. Offending class is '"
                        + classInfo.name() + "'");
            }
        }
        if (candidates.size() != 1) {
            throw new DeploymentException("'@ExposeA2AAgent' can only be placed on a type that has a single method. "
                    + "Offending class is '" + classInfo.name() + "'");
        }
        MethodInfo exposedMethod = candidates.get(0);
        // the return value is what the client receives as an artifact, so there has to be one
        if (exposedMethod.returnType().kind() == Type.Kind.VOID) {
            throw new DeploymentException("'@ExposeA2AAgent' requires a method that returns the agent's answer, and '"
                    + classInfo.name() + "#" + exposedMethod.name() + "' returns void");
        }
        if (DEFERRED_RESULT_TYPES.contains(exposedMethod.returnType().name())) {
            throw new DeploymentException("'@ExposeA2AAgent' requires a method that returns the agent's answer "
                    + "itself, and '" + classInfo.name() + "#" + exposedMethod.name() + "' returns "
                    + exposedMethod.returnType().name().withoutPackagePrefix());
        }
        return exposedMethod;
    }

    private List<MethodInfo> agenticWorkflowMethods(ClassInfo classInfo) {
        List<MethodInfo> workflowMethods = new ArrayList<>();
        for (MethodInfo method : classInfo.methods()) {
            for (DotName workflowAnnotation : AGENTIC_WORKFLOW_ANNOTATIONS) {
                if (method.hasAnnotation(workflowAnnotation)) {
                    workflowMethods.add(method);
                    break;
                }
            }
        }
        return workflowMethods;
    }

    /**
     * A workflow may hand back its result wrapped together with the scope it ran in. The scope is internal
     * bookkeeping, so only the result travels to the client.
     */
    private static Type unwrapAgenticScope(Type returnType) {
        if (!RESULT_WITH_AGENTIC_SCOPE.equals(returnType.name())) {
            return returnType;
        }
        if (returnType.kind() == Type.Kind.PARAMETERIZED_TYPE
                && returnType.asParameterizedType().arguments().size() == 1) {
            return returnType.asParameterizedType().arguments().get(0);
        }
        return Type.create(DotNames.OBJECT, Type.Kind.CLASS);
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
     * Indexes the jars whose packages {@link #registerProtocolTypesForReflection} scans.
     */
    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("org.a2aproject.sdk", "a2a-java-sdk-spec"));
        producer.produce(new IndexDependencyBuildItem("org.a2aproject.sdk", "a2a-java-sdk-spec-grpc"));
        producer.produce(new IndexDependencyBuildItem("com.google.protobuf", "protobuf-java"));
    }

    /**
     * The SDK discovers transports through {@code ServiceLoader} and rejects an Agent Card advertising one it
     * cannot find. Native images resolve service providers at build time.
     */
    @BuildStep
    void registerTransportMetadataProviders(BuildProducer<ServiceProviderBuildItem> serviceProviderProducer) {
        serviceProviderProducer.produce(
                ServiceProviderBuildItem.allProvidersFromClassPath(TRANSPORT_METADATA_SERVICE));
    }

    /**
     * The SDK reads its own settings, such as how long a blocking agent may take, from a classpath resource that
     * each of its modules contributes to.
     */
    @BuildStep
    void includeDefaultConfigurationValues(BuildProducer<NativeImageResourceBuildItem> nativeImageResourceProducer) {
        nativeImageResourceProducer.produce(new NativeImageResourceBuildItem(DEFAULTS_RESOURCE));
    }

    /**
     * The SDK denies every task operation unless a {@code TaskAuthorizationProvider} is present, so that a server
     * cannot serve one user's tasks to another by accident. This extension exposes a single agent with no notion of
     * task ownership, and a denial surfaces as {@code TaskNotFoundError} rather than as an authorization failure,
     * which is hard to diagnose. The check is therefore off unless the application asks for it, by setting
     * {@code a2a.authorization.required=true} and supplying a provider. Only an application that exposes an agent
     * is affected: everywhere else the SDK's own default stands, because merely having this extension on the
     * classpath must not relax a setting the application never asked this extension to serve.
     */
    @BuildStep
    void relaxTaskAuthorization(CombinedIndexBuildItem indexBuildItem,
            BuildProducer<RunTimeConfigurationDefaultBuildItem> runTimeConfigurationProducer) {
        if (indexBuildItem.getIndex().getAnnotations(ExposeA2AAgent.class).isEmpty()) {
            return;
        }
        runTimeConfigurationProducer.produce(new RunTimeConfigurationDefaultBuildItem(AUTHORIZATION_REQUIRED, "false"));
    }

    /**
     * The SDK's {@code MainEventBusProcessorInitializer} observes {@code @Initialized(ApplicationScoped.class)},
     * which Quarkus fires during static initialization. {@link A2AEventBusStarter} replaces it, starting the
     * processor at runtime instead.
     *
     * @see <a href="https://github.com/a2aproject/a2a-java/issues/1032">a2a-java#1032 — no native-image
     *      reachability metadata</a>
     */
    @BuildStep
    void vetoEagerEventBusInitializer(BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformerProducer) {
        annotationsTransformerProducer.produce(new AnnotationsTransformerBuildItem(
                vetoingClassTransformation(Set.of(EVENT_BUS_INITIALIZER))));
    }

    /**
     * The SDK reaches these types reflectively from both ends of a call: protobuf parses a request into its
     * generated messages, and Gson writes the details of an error response. The SDK ships no reachability
     * metadata of its own.
     *
     * @see <a href="https://github.com/a2aproject/a2a-java/issues/1032">a2a-java#1032 — no native-image
     *      reachability metadata</a>
     */
    @BuildStep
    void registerProtocolTypesForReflection(CombinedIndexBuildItem indexBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer) {
        IndexView index = indexBuildItem.getIndex();
        if (index.getAnnotations(ExposeA2AAgent.class).isEmpty()) {
            return;
        }
        Stream<ClassInfo> types = Stream.concat(
                PROTOCOL_TYPE_PACKAGES.stream()
                        .flatMap(packageName -> index.getClassesInPackage(packageName).stream()),
                PROTOBUF_MESSAGE_PACKAGES.stream()
                        .flatMap(packageName -> index.getClassesInPackage(packageName).stream())
                        .filter(A2AServerProcessor::derivesFromProtobuf));

        String[] protocolTypes = types
                .map(classInfo -> classInfo.name().toString())
                .toArray(String[]::new);

        reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(protocolTypes)
                .constructors()
                .methods()
                .fields()
                .reason(A2AServerProcessor.class.getSimpleName() + ": serialized by the A2A SDK")
                .build());
    }

    /**
     * Without these, every error the gRPC binding reports reaches the client as
     * {@code INTERNAL: Generated message class "com.google.rpc.ErrorInfo" missing method "getReason"} instead of
     * the status the error maps to.
     */
    @BuildStep
    void registerGrpcErrorTypesForReflection(CombinedIndexBuildItem indexBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer) {
        if (indexBuildItem.getIndex().getAnnotations(ExposeA2AAgent.class).isEmpty()
                || !QuarkusClassLoader.isClassPresentAtRuntime(GRPC_REFERENCE_IMPL)) {
            return;
        }
        reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(GRPC_ERROR_TYPES)
                .constructors()
                .methods()
                .fields()
                .reason(A2AServerProcessor.class.getSimpleName() + ": error responses of the gRPC binding")
                .build());
    }

    private static boolean derivesFromProtobuf(ClassInfo classInfo) {
        if (classInfo.superName() != null && classInfo.superName().toString().startsWith(PROTOBUF_PACKAGE_PREFIX)) {
            return true;
        }
        return classInfo.interfaceNames().stream()
                .anyMatch(interfaceName -> interfaceName.toString().startsWith(PROTOBUF_PACKAGE_PREFIX));
    }

    /**
     * A non-{@code String} result is serialized into JSON, which reaches it reflectively, along with every type
     * reachable from it: the arguments of a generic result and the types of its fields. The core extension already
     * registers that graph for the result of an AI Service, since it parses the model's answer into it, but an
     * agentic workflow is not an AI Service to it, so the graph of a workflow's result is registered here.
     */
    private void registerResultForReflection(MethodInfo aiServiceMethod,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchyProducer) {
        Type returnType = unwrapAgenticScope(aiServiceMethod.returnType());
        if (DotNames.STRING.equals(returnType.name()) || returnType.kind() == Type.Kind.VOID
                || returnType.kind() == Type.Kind.TYPE_VARIABLE) {
            return;
        }
        reflectiveHierarchyProducer.produce(ReflectiveHierarchyBuildItem.builder(returnType)
                .source(A2AServerProcessor.class.getSimpleName() + ": serialized into an A2A DataPart")
                .build());
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
        if (candidates.size() != 1) {
            throw new DeploymentException("Unable to tell which parameter of '" + aiServiceMethod.declaringClass().name()
                    + "#" + aiServiceMethod.name() + "' carries the message sent by the A2A client. Annotate it with "
                    + "'@UserMessage'");
        }
        return candidates.get(0);
    }

    /**
     * Maps a parameter of the AI Service method onto the part of the A2A request that feeds it. Only two sources
     * exist: the text the client sent, and the context the request belongs to.
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
        if (parameter.hasAnnotation(MEMORY_ID)) {
            // an A2A context groups the interactions that belong together, which is exactly what a memory id is
            return invoke.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(RequestContext.class, "getContextId", String.class),
                    invoke.getMethodParam(0));
        }
        throw new DeploymentException("Parameter '" + parameterLabel(parameter) + "' cannot be filled from an A2A "
                + "request. Only the message itself and a '@MemoryId' are available");
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

    private AnnotationTransformation vetoingClassTransformation(Set<String> classNames) {
        return AnnotationTransformation
                .forClasses()
                .when(tc -> {
                    return classNames.contains(tc.declaration().asClass().name().toString());
                })
                .transform(tc -> tc.add(AnnotationInstance.builder(DotNames.VETOED).buildWithTarget(tc.declaration())));
    }

    private void createAgentCardBean(A2AServerRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            AnnotationInstance exposeInstance,
            MethodInfo aiServiceMethod) {
        AnnotationValue streamingValue = exposeInstance.value("streaming");
        boolean streaming = streamingValue != null ? streamingValue.asBoolean() : false;

        AnnotationValue pushNotificationsValue = exposeInstance.value("pushNotifications");
        boolean pushNotifications = pushNotificationsValue != null ? pushNotificationsValue.asBoolean() : false;

        AnnotationValue extendedAgentCardValue = exposeInstance.value("extendedAgentCard");
        boolean extendedAgentCard = extendedAgentCardValue != null ? extendedAgentCardValue.asBoolean() : false;

        // Extensions are not modelled on the annotation: arbitrary ones can already be contributed through
        // AgentCardBuilderCustomizer, and the protocol has no concrete extension to build a shorthand around.
        List<AgentExtension> extensions = Collections.emptyList();

        // only the parameter carrying the message describes what the agent accepts; a memory id is plumbing
        List<String> defaultInputModes = List.of(mediaTypeOf(determineUserMessageParameter(aiServiceMethod).type()));
        List<String> defaultOutputModes = List.of(mediaTypeOf(unwrapAgenticScope(aiServiceMethod.returnType())));

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

        var configurator = SyntheticBeanBuildItem
                .configure(AgentCardBuilderCustomizer.class)
                .setRuntimeInit()
                .runtimeValue(
                        recorder.staticInfoCustomizer(
                                new AgentCapabilities(streaming, pushNotifications, extendedAgentCard,
                                        extensions),
                                defaultInputModes, defaultOutputModes, skills));

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
     */
    private void generateAgentExecutor(ClassInfo classInfo, MethodInfo aiServiceMethod,
            ClassOutput classOutput) {
        String implClassName = classInfo.name().packagePrefix() + "." + classInfo.simpleName()
                + "$AgentExecutor";
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

                Type returnType = aiServiceMethod.returnType();
                if (RESULT_WITH_AGENTIC_SCOPE.equals(returnType.name())) {
                    aiServiceResultHandle = invoke.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(RESULT_WITH_AGENTIC_SCOPE.toString(), "result",
                                    Object.class.getName()),
                            aiServiceResultHandle);
                    returnType = unwrapAgenticScope(returnType);
                    if (DotNames.STRING.equals(returnType.name())) {
                        aiServiceResultHandle = invoke.checkCast(aiServiceResultHandle, String.class);
                    }
                }

                // a String is prose and travels as a TextPart; anything else is structured data and travels as a
                // DataPart, matching the output mode the Agent Card advertises
                boolean returnsString = DotNames.STRING.equals(returnType.name());
                ResultHandle result = invoke.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(implClassName,
                                returnsString ? "stringResultToParts" : "objectResultToParts", List.class,
                                returnsString ? String.class : Object.class),
                        invoke.getThis(), aiServiceResultHandle);

                invoke.returnValue(result);
            }
        }
    }
}
