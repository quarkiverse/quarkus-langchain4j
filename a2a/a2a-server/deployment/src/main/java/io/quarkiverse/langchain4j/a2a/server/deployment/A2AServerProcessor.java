package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.deployment.DotNames.COMPLETION_STAGE;
import static io.quarkiverse.langchain4j.deployment.DotNames.MULTI;
import static io.quarkiverse.langchain4j.deployment.DotNames.UNI;

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
import jakarta.enterprise.inject.spi.DeploymentException;

import org.a2aproject.sdk.server.PublicAgentCard;
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
import io.quarkiverse.langchain4j.a2a.server.runtime.A2AServerRecorder;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.AgentCardProducer;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.ConfigCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.DetectedTransports;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.ExposedAgent;
import io.quarkiverse.langchain4j.deployment.AiServicesUtil;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

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
     * With no agent to expose there is no card to build, so the beans building it are vetoed.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void beans(CombinedIndexBuildItem indexBuildItem,
            A2AServerRecorder recorder,
            BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformerProducer,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer) {

        IndexView index = indexBuildItem.getIndex();
        Collection<AnnotationInstance> exposeInstances = index.getAnnotations(ExposeA2AAgent.class);
        if (exposeInstances.isEmpty()) {
            annotationsTransformerProducer.produce(new AnnotationsTransformerBuildItem(vetoingClassTransformation(
                    Set.of(AgentCardProducer.class.getName(), ConfigCardBuilderCustomizer.class.getName()))));
            return;
        }
        validateSingleAgent(exposeInstances);

        createDetectedTransportsBean(recorder, syntheticBeanProducer);
        for (AnnotationInstance exposeInstance : exposeInstances) {
            ClassInfo targetClass = exposeInstance.target().asClass();
            MethodInfo aiServiceMethod = determineExposedMethod(targetClass, index);

            createAgentCardBean(recorder, syntheticBeanProducer, exposeInstance, aiServiceMethod);
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
}
