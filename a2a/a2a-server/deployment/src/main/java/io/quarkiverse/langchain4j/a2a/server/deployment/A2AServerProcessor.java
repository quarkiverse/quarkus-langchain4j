package io.quarkiverse.langchain4j.a2a.server.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentExtension;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.Message;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.a2a.server.runtime.A2AServerRecorder;
import io.quarkiverse.langchain4j.a2a.server.runtime.card.AgentCardProducer;
import io.quarkiverse.langchain4j.a2a.server.runtime.executor.QuarkusBaseAgentExecutor;
import io.quarkiverse.langchain4j.deployment.AiServicesUtil;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

public class A2AServerProcessor {

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

            // We veto the io.quarkiverse.langchain4j.a2a.server.runtime.card.AgentCardProducer when there is no agent
            annotationsTransformerProducer.produce(new AnnotationsTransformerBuildItem(vetoingClassTransformation(
                    AgentCardProducer.class.getName())));

            return;
        }
        if (exposeInstances.size() > 1) {
            throw new DeploymentException("Multiple expose instances found for '" + ExposeA2AAgent.class.getName()
                    + "'. Currently, only exposing a single A2A agent is supported");
        }
        AnnotationInstance exposeInstance = exposeInstances.iterator().next();
        ClassInfo targetClass = exposeInstance.target().asClass();
        if (!targetClass.hasDeclaredAnnotation(RegisterAiService.class)) {
            throw new DeploymentException(
                    "'@ExposeA2AAgent' can only be placed on an AI Service that is annotated with @RegisterAiService."
                            + " Offending class is '"
                            + targetClass.name() + "'");
        }

        createAgentCardBean(recorder, syntheticBeanProducer, exposeInstance);
        ClassOutput generatedBeanOutput = new GeneratedBeanGizmoAdaptor(generatedBeanProducer);
        generateAgentExecutor(targetClass, index, generatedBeanOutput);
    }

    private AnnotationTransformation vetoingClassTransformation(String className) {
        return AnnotationTransformation
                .forClasses()
                .when(tc -> {
                    return tc.declaration().asClass().name().toString().equals(className);
                })
                .transform(tc -> tc.add(AnnotationInstance.builder(DotNames.VETOED).buildWithTarget(tc.declaration())));
    }

    private void createAgentCardBean(A2AServerRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            AnnotationInstance exposeInstance) {
        String agentName = exposeInstance.value("name").asString();
        String agentDescription = exposeInstance.value("description").asString();

        AnnotationValue streamingValue = exposeInstance.value("streaming");
        boolean streaming = streamingValue != null ? streamingValue.asBoolean() : false;

        AnnotationValue pushNotificationsValue = exposeInstance.value("pushNotifications");
        boolean pushNotifications = pushNotificationsValue != null ? pushNotificationsValue.asBoolean() : false;

        AnnotationValue stateTransitionHistoryValue = exposeInstance.value("stateTransitionHistory");
        boolean stateTransitionHistory = stateTransitionHistoryValue != null ? stateTransitionHistoryValue.asBoolean() : false;

        // TODO: I have no idea what to do with this
        List<AgentExtension> extensions = Collections.emptyList();

        // TODO: I assume we need to figure these out depending on the the method parameters and return type of the
        //  AI service
        List<String> defaultInputModes = Collections.emptyList();
        List<String> defaultOutputModes = Collections.emptyList();

        AnnotationInstance[] skillInstances = exposeInstance.value("skills").asNestedArray();
        List<AgentSkill> skills = new ArrayList<>();
        for (AnnotationInstance skillInstance : skillInstances) {
            AgentSkill.Builder skillBuilder = new AgentSkill.Builder();
            skillBuilder.id(skillInstance.value("id").asString());
            skillBuilder.name(skillInstance.value("name").asString());
            skillBuilder.description(skillInstance.value("description").asString());
            String[] tags = skillInstance.value("tags").asStringArray();
            if (tags != null) {
                skillBuilder.tags(Arrays.asList(tags));
            }
            String[] examples = skillInstance.value("examples").asStringArray();
            if (examples != null) {
                skillBuilder.examples(Arrays.asList(examples));
            }
            skills.add(skillBuilder.build());
        }

        var configurator = SyntheticBeanBuildItem
                .configure(AgentCardBuilderCustomizer.class)
                .setRuntimeInit()
                .runtimeValue(
                        recorder.staticInfoCustomizer(agentName, agentDescription,
                                new AgentCapabilities(streaming, pushNotifications, stateTransitionHistory,
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
     *     protected List<Part<?>> invoke(Message message) {
     *         String aiServiceResult = aiService.chat(textPartsToString(message));
     *         return stringResultToParts(aiServiceResult);
     *     }
     * }
     * </pre>
     */
    private void generateAgentExecutor(ClassInfo classInfo, IndexView index,
            ClassOutput classOutput) {
        List<MethodInfo> aiServiceMethods = AiServicesUtil.determineAiServiceMethods(classInfo, index);
        if (aiServiceMethods.size() != 1) {
            throw new DeploymentException(
                    "'@ExposeA2AAgent' can only be placed on an AI Service that has a single method. Offending class "
                            + "is '"
                            + classInfo.name() + "'");
        }

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
                                Message.class));
                invoke.setModifiers(Modifier.PROTECTED);

                MethodInfo aiServiceMethod = aiServiceMethods.iterator().next();

                List<ResultHandle> aiServiceMethodParamHandles = new ArrayList<>();
                List<MethodParameterInfo> aiServiceMethodParams = aiServiceMethod.parameters();
                if (aiServiceMethodParams.size() != 1) {
                    // TODO: implement
                    throw new RuntimeException("Not yet implemented");
                }
                aiServiceMethodParams.forEach(p -> {
                    if (!p.type().name().equals(DotNames.STRING)) {
                        // TODO: implement
                        throw new RuntimeException("Not yet implemented");
                    }
                    aiServiceMethodParamHandles.add(invoke.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(implClassName, "textPartsToString", String.class,
                                    Message.class),
                            invoke.getThis(), invoke.getMethodParam(0)));
                });

                ResultHandle aiServiceResultHandle = invoke.invokeInterfaceMethod(MethodDescriptor.of(aiServiceMethod),
                        invoke.readInstanceField(aiServiceField, invoke.getThis()),
                        aiServiceMethodParamHandles.toArray(new ResultHandle[aiServiceMethodParams.size()]));

                if (!aiServiceMethod.returnType().name().equals(DotNames.STRING)) {
                    // TODO: implement
                    throw new RuntimeException("Not yet implemented");
                }

                ResultHandle result = invoke.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(implClassName, "stringResultToParts", List.class,
                                String.class),
                        invoke.getThis(), aiServiceResultHandle);

                invoke.returnValue(result);
            }
        }
    }
}
