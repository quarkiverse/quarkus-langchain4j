package io.quarkiverse.langchain4j.deployment;

import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static io.quarkiverse.langchain4j.deployment.ExceptionUtil.illegalConfigurationForMethod;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.MEMORY_ID;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.REGISTER_AI_SERVICES;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.SEED_MEMORY;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.TOOL_INPUT_GUARDRAIL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.TOOL_INPUT_GUARDRAILS;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.TOOL_OUTPUT_GUARDRAIL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.TOOL_OUTPUT_GUARDRAILS;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.V;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.VOID_CLASS;
import static io.quarkiverse.langchain4j.deployment.MethodParameterAsTemplateVariableAllowance.FORCE_ALLOW;
import static io.quarkiverse.langchain4j.deployment.MethodParameterAsTemplateVariableAllowance.IGNORE;
import static io.quarkiverse.langchain4j.deployment.MethodParameterAsTemplateVariableAllowance.OPTIONAL_DENY;
import static io.quarkiverse.langchain4j.deployment.ObjectSubstitutionUtil.registerJsonSchema;
import static io.quarkiverse.langchain4j.runtime.types.TypeUtil.isMulti;
import static io.quarkus.arc.processor.DotNames.NAMED;
import static io.quarkus.arc.processor.DotNames.SINGLETON;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.InterceptorBinding;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.inject.RestClient;
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
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.output.JsonSchemas;
import dev.langchain4j.service.output.ServiceOutputParser;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import dev.langchain4j.spi.classloading.ClassInstanceFactory;
import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.deployment.DeclarativeAiServiceBuildItem.DeclarativeAiServiceInputGuardrails;
import io.quarkiverse.langchain4j.deployment.DeclarativeAiServiceBuildItem.DeclarativeAiServiceOutputGuardrails;
import io.quarkiverse.langchain4j.deployment.config.LangChain4jBuildConfig;
import io.quarkiverse.langchain4j.deployment.devui.ToolProviderInfo;
import io.quarkiverse.langchain4j.deployment.items.AiServicesMethodBuildItem;
import io.quarkiverse.langchain4j.deployment.items.MethodParameterAllowedAnnotationsBuildItem;
import io.quarkiverse.langchain4j.deployment.items.MethodParameterIgnoredAnnotationsBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ToolMethodBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ToolQualifierProvider;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailsLiteral;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailsLiteral;
import io.quarkiverse.langchain4j.runtime.AiServicesRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.runtime.QuarkusServiceOutputParser;
import io.quarkiverse.langchain4j.runtime.RequestScopeStateDefaultMemoryIdProvider;
import io.quarkiverse.langchain4j.runtime.ResponseSchemaUtil;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo.ResponseSchemaInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodImplementationSupport;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemoryRemovable;
import io.quarkiverse.langchain4j.runtime.aiservice.ChatMemorySeeder;
import io.quarkiverse.langchain4j.runtime.aiservice.ComponentResolutionMode;
import io.quarkiverse.langchain4j.runtime.aiservice.DeclarativeAiServiceCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.DeclarativeAiServiceCreateInfo.ComponentEntry;
import io.quarkiverse.langchain4j.runtime.aiservice.MetricsCountedWrapper;
import io.quarkiverse.langchain4j.runtime.aiservice.MetricsTimedWrapper;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;
import io.quarkiverse.langchain4j.runtime.aiservice.SpanWrapper;
import io.quarkiverse.langchain4j.runtime.aiservice.ThinkingEmitted;
import io.quarkiverse.langchain4j.runtime.aiservice.ThinkingHandler;
import io.quarkiverse.langchain4j.runtime.config.GuardrailsConfig;
import io.quarkiverse.langchain4j.runtime.types.TypeSignatureParser;
import io.quarkiverse.langchain4j.runtime.types.TypeUtil;
import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.quarkiverse.langchain4j.spi.PromptTemplateFactoryContentFilterProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.CustomScopeAnnotationsBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.SynthesisFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.arc.processor.BuiltinScope;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.deployment.metrics.MetricsCapabilityBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.qute.Expression;
import io.quarkus.runtime.metrics.MetricsFactory;
import io.smallrye.mutiny.Multi;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class AiServicesProcessor {

    private static final Logger log = Logger.getLogger(AiServicesProcessor.class);

    private static final DotName TOOLBOX = DotName.createSimple(ToolBox.class);
    public static final DotName MICROMETER_TIMED = DotName.createSimple("io.micrometer.core.annotation.Timed");
    public static final DotName MICROMETER_COUNTED = DotName.createSimple("io.micrometer.core.annotation.Counted");
    private static final DotName INVOCATION_PARAMETERS = DotName.createSimple(InvocationParameters.class);
    private static final DotName CHAT_REQUEST_PARAMETERS = DotName.createSimple(ChatRequestParameters.class);
    public static final String DEFAULT_DELIMITER = "\n";
    public static final Predicate<AnnotationInstance> IS_METHOD_PARAMETER_ANNOTATION = ai -> ai.target()
            .kind() == AnnotationTarget.Kind.METHOD_PARAMETER;
    private static final Function<AnnotationInstance, Integer> METHOD_PARAMETER_POSITION_FUNCTION = ai -> Integer
            .valueOf(ai.target()
                    .asMethodParameter().position());

    public static final MethodDescriptor OBJECT_CONSTRUCTOR = MethodDescriptor.ofConstructor(Object.class);
    private static final MethodDescriptor RECORDER_METHOD_CREATE_INFO = MethodDescriptor.ofMethod(AiServicesRecorder.class,
            "getAiServiceMethodCreateInfo", AiServiceMethodCreateInfo.class, String.class, String.class);
    private static final MethodDescriptor SUPPORT_IMPLEMENT = MethodDescriptor.ofMethod(
            AiServiceMethodImplementationSupport.class,
            "implement", Object.class, AiServiceMethodImplementationSupport.Input.class);

    private static final MethodDescriptor QUARKUS_AI_SERVICES_CONTEXT_CLOSE = MethodDescriptor.ofMethod(
            QuarkusAiServiceContext.class, "close", void.class);

    private static final MethodDescriptor QUARKUS_AI_SERVICES_CONTEXT_REMOVE_CHAT_MEMORY_IDS = MethodDescriptor.ofMethod(
            QuarkusAiServiceContext.class, "removeChatMemoryIds", void.class, Object[].class);
    private static final MethodDescriptor QUARKUS_AI_SERVICES_CONTEXT_EVICT_CHAT_MEMORY = MethodDescriptor.ofMethod(
            QuarkusAiServiceContext.class, "evictChatMemory", boolean.class, Object.class);
    private static final MethodDescriptor QUARKUS_AI_SERVICES_CONTEXT_GET_CHAT_MEMORY = MethodDescriptor.ofMethod(
            QuarkusAiServiceContext.class, "getChatMemory", ChatMemory.class, Object.class);
    private static final MethodDescriptor QUARKUS_AI_SERVICES_CONTEXT_CLEAR_CHAT_MEMORY = MethodDescriptor
            .ofMethod(
                    QuarkusAiServiceContext.class, "clearChatMemory", void.class);
    private static final MethodDescriptor QUARKUS_AI_SERVICES_CONTEXT_GET_ALL_CHAT_MEMORY_IDS = MethodDescriptor
            .ofMethod(
                    QuarkusAiServiceContext.class, "getAllChatMemoryIds", Collection.class);

    public static final MethodDescriptor CHAT_MEMORY_SEEDER_CONTEXT_METHOD_NAME = MethodDescriptor
            .ofMethod(ChatMemorySeeder.Context.class, "methodName", String.class);

    private static final DotName CHAT_MEMORY_ACCESS = DotName.createSimple(
            ChatMemoryAccess.class);

    private static final String METRICS_DEFAULT_NAME = "langchain4j.aiservices";

    private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final ResultHandle[] EMPTY_RESULT_HANDLES_ARRAY = new ResultHandle[0];

    private static final ServiceOutputParser SERVICE_OUTPUT_PARSER = new QuarkusServiceOutputParser(); // TODO: this might need to be improved

    private static final Set<DotName> GUARDRAIL_ANNOTATIONS = Set.of(
            TOOL_INPUT_GUARDRAIL, TOOL_INPUT_GUARDRAILS, TOOL_OUTPUT_GUARDRAIL, TOOL_OUTPUT_GUARDRAILS);

    @BuildStep
    public void nativeSupport(CombinedIndexBuildItem indexBuildItem,
            List<AiServicesMethodBuildItem> aiServicesMethodBuildItems,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<ServiceProviderBuildItem> serviceProviderProducer) {
        IndexView index = indexBuildItem.getIndex();
        Collection<AnnotationInstance> instances = index.getAnnotations(LangChain4jDotNames.DESCRIPTION);
        Set<ClassInfo> classesUsingDescription = new HashSet<>();
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.FIELD) {
                continue;
            }
            classesUsingDescription.add(instance.target().asField().declaringClass());
        }
        if (!classesUsingDescription.isEmpty()) {
            reflectiveClassProducer.produce(ReflectiveClassBuildItem
                    .builder(classesUsingDescription.stream().map(i -> i.name().toString()).toArray(String[]::new)).fields(true)
                    .build());
        }
        Set<DotName> returnTypesToRegister = new HashSet<>();
        for (AiServicesMethodBuildItem aiServicesMethodBuildItem : aiServicesMethodBuildItems) {
            Type type = aiServicesMethodBuildItem.getMethodInfo().returnType();
            if (type.kind() == Type.Kind.PRIMITIVE) {
                continue;
            }
            DotName returnTypeName = type.name();
            if (returnTypeName.toString().startsWith("java.")) {
                continue;
            }
            returnTypesToRegister.add(returnTypeName);
        }
        if (!returnTypesToRegister.isEmpty()) {
            reflectiveClassProducer.produce(ReflectiveClassBuildItem
                    .builder(returnTypesToRegister.stream().map(DotName::toString).toArray(String[]::new))
                    .constructors().fields().methods()
                    .build());
        }

        serviceProviderProducer.produce(new ServiceProviderBuildItem(DefaultMemoryIdProvider.class.getName(),
                RequestScopeStateDefaultMemoryIdProvider.class.getName()));
        serviceProviderProducer.produce(ServiceProviderBuildItem.allProvidersFromClassPath(
                PromptTemplateFactoryContentFilterProvider.class.getName()));
        serviceProviderProducer.produce(ServiceProviderBuildItem.allProvidersFromClassPath(
                ClassMetadataProviderFactory.class.getName()));
        serviceProviderProducer.produce(ServiceProviderBuildItem.allProvidersFromClassPath(
                ClassInstanceFactory.class.getName()));

        // needed because various LLMs use these, so let's be proactive
        // there isn't one great place to put this, so this is probably as good as any
        reflectiveClassProducer.produce(
                ReflectiveClassBuildItem.builder(PropertyNamingStrategies.SnakeCaseStrategy.class).constructors().build());
        reflectiveClassProducer.produce(
                ReflectiveClassBuildItem.builder(PropertyNamingStrategies.LowerCamelCaseStrategy.class).constructors().build());
    }

    @BuildStep
    public void validateToolsPerAiService(BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> validation,
            CombinedIndexBuildItem indexBuildItem) {
        // Validate that from a given @RegisterAiServices class, the tool names are unique across all tools and tool boxes.
        IndexView index = indexBuildItem.getIndex();
        Collection<AnnotationInstance> instances = index.getAnnotations(LangChain4jDotNames.REGISTER_AI_SERVICES);
        for (AnnotationInstance instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue; // should never happen
            }
            ClassInfo declarativeAiServiceClassInfo = instance.target().asClass();
            List<ClassInfo> tools = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            Set<String> toolNames = new HashSet<>();
            // Check @RegisterAiServices.tools
            for (ClassInfo toolClass : tools(instance, index)) {
                if (visited.contains(toolClass.name().toString())) {
                    continue;
                }
                tools.add(toolClass);
                visited.add(toolClass.name().toString());
                Set<String> currentToolNames = gatherToolNames(toolClass, index);
                for (String toolName : currentToolNames) {
                    if (toolNames.contains(toolName)) {
                        validation.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(new IllegalStateException(
                                "Duplicate tool name '" + toolName + "' found in tools for ai services class '"
                                        + declarativeAiServiceClassInfo.name() + "'.")));
                    }
                    toolNames.add(toolName);
                }
            }
            // Check @RegisterAiServices.tools + service method @ToolBox
            for (MethodInfo serviceMethodInfo : declarativeAiServiceClassInfo.methods()) {
                if (serviceMethodInfo.hasAnnotation(TOOLBOX)) {
                    AnnotationInstance toolBoxInstance = serviceMethodInfo.declaredAnnotation(TOOLBOX);
                    if (toolBoxInstance == null) {
                        continue;
                    }
                    for (String methodToolClassName : gatherMethodToolClassNames(serviceMethodInfo)) {
                        if (visited.contains(methodToolClassName)) {
                            continue;
                        }
                        visited.add(methodToolClassName);
                        Set<String> currentToolNames = gatherToolNames(index.getClassByName(methodToolClassName), index);
                        for (String toolName : currentToolNames) {
                            if (toolNames.contains(toolName)) {
                                validation.produce(
                                        new ValidationPhaseBuildItem.ValidationErrorBuildItem(new IllegalStateException(
                                                "Duplicate tool name '" + toolName + "' found in tools for ai services method '"
                                                        + serviceMethodInfo.name() + "' of class '"
                                                        + declarativeAiServiceClassInfo.name() + "'.")));
                            }
                            toolNames.add(toolName);
                        }
                    }
                }

                checkGuardrailOnAiServiceMethod(serviceMethodInfo);
            }
        }
    }

    /**
     * Check that the given method does not have any tool guardrail annotations, and logs a warning if it does.
     */
    private static void checkGuardrailOnAiServiceMethod(MethodInfo agentAiMethod) {
        if (agentAiMethod.hasAnnotation(DotNames.TOOL)) {
            return;
        }

        List<DotName> dotNames = GUARDRAIL_ANNOTATIONS.stream()
                .filter(agentAiMethod::hasAnnotation)
                .toList();

        if (!dotNames.isEmpty()) {
            log.warnf(
                    "AI service method '%s#%s' is annotated with %s, but tool guardrail annotations apply only to @Tool methods. "
                            +
                            "Please remove the guardrail annotations or annotate the method with @Tool if it's meant to be a tool method.",
                    agentAiMethod.declaringClass().name(),
                    agentAiMethod.name(),
                    dotNames);
        }
    }

    private static Set<String> gatherToolNames(ClassInfo toolClass, IndexView index) {
        Set<String> toolNames = new HashSet<>();
        Set<String> seenMethodSignatures = new HashSet<>();
        ClassInfo current = toolClass;
        while (current != null && !DotNames.OBJECT.equals(current.name())) {
            collectToolNames(current, seenMethodSignatures, toolNames);
            for (DotName ifaceName : current.interfaceNames()) {
                ClassInfo iface = index.getClassByName(ifaceName);
                if (iface != null) {
                    collectToolNames(iface, seenMethodSignatures, toolNames);
                }
            }
            DotName superName = current.superName();
            current = superName != null ? index.getClassByName(superName) : null;
        }
        return toolNames;
    }

    private static void collectToolNames(ClassInfo classInfo, Set<String> seenMethodSignatures, Set<String> toolNames) {
        for (MethodInfo method : classInfo.methods()) {
            String sig = method.name() + method.parameterTypes();
            if (!seenMethodSignatures.add(sig)) {
                continue;
            }
            String toolName = ToolProcessor.resolveToolName(method);
            if (toolName == null) {
                continue;
            }
            toolNames.add(toolName);
        }
    }

    @BuildStep
    public void findDeclarativeServices(CombinedIndexBuildItem indexBuildItem,
            CustomScopeAnnotationsBuildItem customScopes,
            List<AnnotationsImpliesAiServiceBuildItem> annotationsImpliesAiServiceItems,
            BuildProducer<RequestChatModelBeanBuildItem> requestChatModelBeanProducer,
            BuildProducer<RequestModerationModelBeanBuildItem> requestModerationModelBeanProducer,
            BuildProducer<RequestImageModelBeanBuildItem> requestImageModelBeanProducer,
            BuildProducer<DeclarativeAiServiceBuildItem> declarativeAiServiceProducer,
            BuildProducer<ToolProviderMetaBuildItem> toolProviderProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer,
            BuildProducer<GeneratedBeanBuildItem> generatedBeanProducer,
            BuildProducer<GeneratedClassBuildItem> generatedClassProducer) {
        IndexView index = indexBuildItem.getIndex();

        Set<String> chatModelNames = new HashSet<>();
        Set<String> moderationModelNames = new HashSet<>();
        Set<String> imageModelNames = new HashSet<>();
        List<ToolProviderInfo> toolProviderInfos = new ArrayList<>();
        ClassOutput generatedClassOutput = new GeneratedClassGizmoAdaptor(generatedClassProducer, true);

        Set<DotName> annotationsThatImplyAiService = annotationsImpliesAiServiceItems.stream().flatMap(
                bi -> bi.getAnnotationNames().stream())
                .collect(Collectors.toSet());

        Collection<AnnotationInstance> registerAiServicesInstances = new ArrayList<>(
                index.getAnnotations(REGISTER_AI_SERVICES));

        Set<AnnotationInstance> impliedRegisterAiServiceInstance = determinedImpliedRegisterAiService(
                annotationsThatImplyAiService, index);
        Set<DotName> impliedRegisterAiServiceTarget = impliedRegisterAiServiceInstance.stream()
                .map(ai -> ai.target().asClass().name()).collect(Collectors.toSet());
        registerAiServicesInstances.addAll(impliedRegisterAiServiceInstance);
        Set<DotName> alreadyHandled = new HashSet<>();
        for (AnnotationInstance instance : registerAiServicesInstances) {
            if (instance.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue; // should never happen
            }
            ClassInfo declarativeAiServiceClassInfo = instance.target().asClass();
            if (alreadyHandled.contains(declarativeAiServiceClassInfo.name())) {
                continue;
            }
            alreadyHandled.add(declarativeAiServiceClassInfo.name());

            // Model selection via modelName + CDI
            String chatModelName = chatModelName(instance, chatModelNames);

            // Resolve component attributes using tri-state model.
            // Use valueWithDefault so annotation defaults (e.g. ChatMemoryProvider.class for chatMemoryProvider)
            // are applied when the attribute is not explicitly set by the user.
            ComponentResolution chatMemoryProviderResolution = resolveComponent(
                    instance.valueWithDefault(index, "chatMemoryProvider"), LangChain4jDotNames.CHAT_MEMORY_PROVIDER);
            if (chatMemoryProviderResolution.mode() == ComponentResolutionMode.EXPLICIT) {
                validateClassExistsAndRegister(chatMemoryProviderResolution.className(), index,
                        reflectiveClassProducer, unremovableBeanProducer);
            }

            ComponentResolution chatMemoryFlushStrategyResolution = resolveComponent(
                    instance.valueWithDefault(index, "chatMemoryFlushStrategy"), null);
            if (chatMemoryFlushStrategyResolution.mode() == ComponentResolutionMode.EXPLICIT) {
                validateClassExistsAndRegister(chatMemoryFlushStrategyResolution.className(), index,
                        reflectiveClassProducer, unremovableBeanProducer);
            }

            ComponentResolution retrievalAugmentorResolution = resolveComponent(
                    instance.valueWithDefault(index, "retrievalAugmentor"), LangChain4jDotNames.RETRIEVAL_AUGMENTOR);
            if (retrievalAugmentorResolution.mode() == ComponentResolutionMode.EXPLICIT) {
                validateClassExistsAndRegister(retrievalAugmentorResolution.className(), index,
                        reflectiveClassProducer, unremovableBeanProducer);
            }

            ComponentResolution moderationModelResolution = resolveComponent(
                    instance.valueWithDefault(index, "moderationModel"), LangChain4jDotNames.MODERATION_MODEL);
            if (moderationModelResolution.mode() == ComponentResolutionMode.EXPLICIT) {
                validateClassExistsAndRegister(moderationModelResolution.className(), index,
                        reflectiveClassProducer, unremovableBeanProducer);
            }

            ComponentResolution imageModelResolution = resolveComponent(
                    instance.valueWithDefault(index, "imageModel"), LangChain4jDotNames.IMAGE_MODEL);
            if (imageModelResolution.mode() == ComponentResolutionMode.EXPLICIT) {
                validateClassExistsAndRegister(imageModelResolution.className(), index,
                        reflectiveClassProducer, unremovableBeanProducer);
            }

            ComponentResolution toolProviderResolution = resolveComponent(
                    instance.valueWithDefault(index, "toolProvider"), LangChain4jDotNames.TOOL_PROVIDER);
            if (toolProviderResolution.mode() == ComponentResolutionMode.EXPLICIT) {
                validateClassExistsAndRegister(toolProviderResolution.className(), index,
                        reflectiveClassProducer, unremovableBeanProducer);
                toolProviderInfos.add(new ToolProviderInfo(toolProviderResolution.className().toString(),
                        declarativeAiServiceClassInfo.simpleName()));
            }

            ComponentResolution toolSearchStrategyResolution = resolveComponent(
                    instance.valueWithDefault(index, "toolSearchStrategy"), LangChain4jDotNames.TOOL_SEARCH_STRATEGY);
            if (toolSearchStrategyResolution.mode() == ComponentResolutionMode.EXPLICIT) {
                validateClassExistsAndRegister(toolSearchStrategyResolution.className(), index,
                        reflectiveClassProducer, unremovableBeanProducer);
            }

            ComponentResolution toolHallucinationStrategyResolution = resolveComponent(
                    instance.valueWithDefault(index, "toolHallucinationStrategy"), null);

            ComponentResolution systemMessageProviderResolution = resolveComponent(
                    instance.valueWithDefault(index, "systemMessageProvider"), null);
            if (systemMessageProviderResolution.mode() == ComponentResolutionMode.EXPLICIT) {
                validateClassExistsAndRegister(systemMessageProviderResolution.className(), index,
                        reflectiveClassProducer, unremovableBeanProducer);
            }

            // determine if the AiService returns an image
            for (MethodInfo method : declarativeAiServiceClassInfo.methods()) {
                Type returnType = method.returnType();
                if (isImageOrImageResultResult(returnType)) {
                    imageModelNames.add(chatModelName);
                }
            }

            // determine whether the method is annotated with @Moderate
            String moderationModelName = NamedConfigUtil.DEFAULT_NAME;
            for (MethodInfo method : declarativeAiServiceClassInfo.methods()) {
                if (method.hasAnnotation(LangChain4jDotNames.MODERATE)) {
                    if (moderationModelResolution.mode() != ComponentResolutionMode.EXPLICIT) {
                        AnnotationValue modelNameValue = instance.value("modelName");
                        if (modelNameValue != null) {
                            String modelNameValueStr = modelNameValue.asString();
                            if ((modelNameValueStr != null) && !modelNameValueStr.isEmpty()) {
                                moderationModelName = modelNameValueStr;
                            }
                        }
                        moderationModelNames.add(moderationModelName);
                    }
                    break;
                }
            }

            String imageModelName = chatModelName; // TODO: should we have a separate setting for this?

            List<ClassInfo> tools = tools(instance, index);
            if (!tools.isEmpty() && chatMemoryProviderResolution.mode() == ComponentResolutionMode.SKIP) {
                throw new IllegalArgumentException("Tool usage requires chat memory. Offending AiService is '"
                        + declarativeAiServiceClassInfo.name() + "'");
            }
            Integer maxToolCallingRoundTrips = 0;
            AnnotationValue maxToolCallingRoundTripsValue = instance.value("maxToolCallingRoundTrips");
            if (maxToolCallingRoundTripsValue != null) {
                maxToolCallingRoundTrips = maxToolCallingRoundTripsValue.asInt();
            }

            Integer maxToolCallsPerResponse = instance.value("maxToolCallsPerResponse") != null
                    ? instance.value("maxToolCallsPerResponse").asInt()
                    : 0;
            if (maxToolCallsPerResponse < 0) {
                throw new IllegalArgumentException(
                        "maxToolCallsPerResponse must be 0 or greater, but was: " + maxToolCallsPerResponse);
            }

            boolean allowContinuousForcedToolCalling = instance.value("allowContinuousForcedToolCalling") != null
                    ? instance.value("allowContinuousForcedToolCalling").asBoolean()
                    : false;

            boolean shouldThrowExceptionOnEventError = instance.value("shouldThrowExceptionOnEventError") != null
                    ? instance.value("shouldThrowExceptionOnEventError").asBoolean()
                    : false;

            declarativeAiServiceProducer.produce(
                    new DeclarativeAiServiceBuildItem(
                            declarativeAiServiceClassInfo,
                            tools,
                            chatMemoryProviderResolution.className(),
                            chatMemoryProviderResolution.mode(),
                            chatMemoryFlushStrategyResolution.className(),
                            chatMemoryFlushStrategyResolution.mode(),
                            retrievalAugmentorResolution.className(),
                            retrievalAugmentorResolution.mode(),
                            moderationModelResolution.className(),
                            moderationModelResolution.mode(),
                            imageModelResolution.className(),
                            imageModelResolution.mode(),
                            toolProviderResolution.className(),
                            toolProviderResolution.mode(),
                            toolSearchStrategyResolution.className(),
                            toolSearchStrategyResolution.mode(),
                            toolHallucinationStrategyResolution.className(),
                            toolHallucinationStrategyResolution.mode(),
                            systemMessageProviderResolution.className(),
                            systemMessageProviderResolution.mode(),
                            determineChatMemorySeeder(declarativeAiServiceClassInfo, generatedClassOutput),
                            determineThinkingHandler(declarativeAiServiceClassInfo, generatedClassOutput),
                            cdiScope(customScopes, declarativeAiServiceClassInfo),
                            chatModelName,
                            moderationModelName,
                            imageModelName,
                            beanName(declarativeAiServiceClassInfo),
                            classInputGuardrails(declarativeAiServiceClassInfo, index),
                            classOutputGuardrails(declarativeAiServiceClassInfo, index),
                            toolArgumentsErrorHandlerDotName(declarativeAiServiceClassInfo, generatedBeanProducer),
                            toolExecutionErrorHandlerDotName(declarativeAiServiceClassInfo, generatedBeanProducer),
                            maxToolCallingRoundTrips,
                            maxToolCallsPerResponse,
                            allowContinuousForcedToolCalling,
                            // we need to make these @DefaultBean because there could be other CDI beans of the same type that need to take precedence
                            impliedRegisterAiServiceTarget.contains(declarativeAiServiceClassInfo.name()),
                            shouldThrowExceptionOnEventError));

        }
        toolProviderProducer.produce(new ToolProviderMetaBuildItem(toolProviderInfos));

        for (String chatModelName : chatModelNames) {
            requestChatModelBeanProducer.produce(new RequestChatModelBeanBuildItem(chatModelName));
        }

        for (String moderationModelName : moderationModelNames) {
            requestModerationModelBeanProducer.produce(new RequestModerationModelBeanBuildItem(moderationModelName));
        }

        for (String imageModelName : imageModelNames) {
            requestImageModelBeanProducer.produce(new RequestImageModelBeanBuildItem(imageModelName));
        }
    }

    private static Set<AnnotationInstance> determinedImpliedRegisterAiService(Set<DotName> annotationsThatImplyAiService,
            IndexView index) {
        Set<AnnotationInstance> impliedDefaultRegisterAiService = new HashSet<>();
        for (DotName ann : annotationsThatImplyAiService) {
            index.getAnnotations(ann).forEach(instance -> {
                ClassInfo ci;
                switch (instance.target().kind()) {
                    case METHOD -> {
                        ci = instance.target().asMethod().declaringClass();
                    }
                    case CLASS -> {
                        ci = instance.target().asClass();
                    }
                    case FIELD -> {
                        ci = instance.target().asField().declaringClass();
                    }
                    case METHOD_PARAMETER -> {
                        ci = instance.target().asMethodParameter().method().declaringClass();
                    }
                    default -> {
                        ci = null;
                    }
                }
                if (ci == null) {
                    return;
                }
                if (!ci.isInterface()) {
                    return;
                }
                impliedDefaultRegisterAiService.add(AnnotationInstance.builder(REGISTER_AI_SERVICES).buildWithTarget(ci));
            });
        }
        return impliedDefaultRegisterAiService;
    }

    private static String chatModelName(AnnotationInstance instance, Set<String> chatModelNames) {
        String chatModelName = NamedConfigUtil.DEFAULT_NAME;
        AnnotationValue modelNameValue = instance.value("modelName");
        if (modelNameValue != null) {
            String modelNameValueStr = modelNameValue.asString();
            if ((modelNameValueStr != null) && !modelNameValueStr.isEmpty()) {
                chatModelName = modelNameValueStr;
            }
        }
        chatModelNames.add(chatModelName);
        return chatModelName;
    }

    private static Optional<String> beanName(ClassInfo declarativeAiServiceClassInfo) {
        AnnotationInstance namedAnno = declarativeAiServiceClassInfo.annotation(NAMED);
        Optional<String> beanName = Optional.empty();
        if (namedAnno != null) {
            beanName = Optional.ofNullable(namedAnno.value().asString());
        }
        return beanName;
    }

    private static DotName cdiScope(CustomScopeAnnotationsBuildItem customScopes, ClassInfo declarativeAiServiceClassInfo) {
        DotName cdiScope = BuiltinScope.REQUEST.getInfo().getDotName();
        Optional<AnnotationInstance> scopeAnnotation = customScopes.getScope(declarativeAiServiceClassInfo.annotations());
        if (scopeAnnotation.isPresent()) {
            cdiScope = scopeAnnotation.get().name();
        }
        return cdiScope;
    }

    // imageModelSupplierClassName and chatMemoryProviderSupplierClassDotName methods removed
    // — resolution is now handled by resolveComponent() in findDeclarativeServices

    private static DeclarativeAiServiceInputGuardrails classInputGuardrails(ClassInfo aiServiceClassInfo, IndexView index) {
        var inputGuardrailsAnnotation = Optional
                .ofNullable(aiServiceClassInfo.annotation(LangChain4jDotNames.INPUT_GUARDRAILS));
        return new DeclarativeAiServiceInputGuardrails(classGuardrails(inputGuardrailsAnnotation, index));
    }

    private static DeclarativeAiServiceOutputGuardrails classOutputGuardrails(ClassInfo aiServiceClassInfo, IndexView index) {
        var outputGuardrailsAnnotation = Optional
                .ofNullable(aiServiceClassInfo.annotation(LangChain4jDotNames.OUTPUT_GUARDRAILS));
        var maxRetries = outputGuardrailsAnnotation
                .map(a -> a.value("maxRetries"))
                .map(AnnotationValue::asInt)
                .orElse(GuardrailsConfig.MAX_RETRIES_DEFAULT);

        return new DeclarativeAiServiceOutputGuardrails(classGuardrails(outputGuardrailsAnnotation, index), maxRetries);
    }

    private static List<ClassInfo> classGuardrails(Optional<AnnotationInstance> annotation, IndexView index) {
        return gatherGuardrailsStream(annotation)
                .map(Type::name)
                .map(index::getClassByName)
                .toList();
    }

    private static InputGuardrailsLiteral classInputGuardrails(DeclarativeAiServiceBuildItem declarativeAiServiceBuildItem) {
        return new InputGuardrailsLiteral(declarativeAiServiceBuildItem.getInputGuardrails().asClassNames());
    }

    private static OutputGuardrailsLiteral classOutputGuardrails(DeclarativeAiServiceBuildItem declarativeAiServiceBuildItem) {
        return new OutputGuardrailsLiteral(
                declarativeAiServiceBuildItem.getOutputGuardrails().asClassNames(),
                declarativeAiServiceBuildItem.getOutputGuardrails().maxRetries());
    }

    private DotName toolArgumentsErrorHandlerDotName(ClassInfo aiServiceClassInfo,
            BuildProducer<GeneratedBeanBuildItem> generatedBean) {
        return toolErrorHandlerDotName(aiServiceClassInfo, LangChain4jDotNames.HANDLE_TOOL_ARGUMENT_ERROR, generatedBean,
                ToolArgumentsErrorHandler.class);
    }

    private DotName toolExecutionErrorHandlerDotName(ClassInfo aiServiceClassInfo,
            BuildProducer<GeneratedBeanBuildItem> generatedBean) {
        return toolErrorHandlerDotName(aiServiceClassInfo, LangChain4jDotNames.HANDLE_TOOL_EXECUTION_ERROR, generatedBean,
                ToolExecutionErrorHandler.class);
    }

    private DotName toolErrorHandlerDotName(ClassInfo aiServiceClassInfo, DotName annotationName,
            BuildProducer<GeneratedBeanBuildItem> generatedBean,
            Class<?> interfaceType) {
        List<AnnotationInstance> instances = aiServiceClassInfo.annotations(annotationName);
        if (instances.isEmpty()) {
            return null;
        }
        if (instances.size() > 1) {
            throw new IllegalConfigurationException(
                    "`@%s` can be used only once in an AI Service. Offending class is '%s'".formatted(annotationName,
                            aiServiceClassInfo.name()));
        }
        AnnotationTarget target = instances.get(0).target();
        if (target.kind() != AnnotationTarget.Kind.METHOD) {
            throw new IllegalConfigurationException(
                    "`@%s` can be used only methods. Offending class is '%s'".formatted(annotationName,
                            aiServiceClassInfo.name()));
        }
        MethodInfo targetMethod = target.asMethod();
        if (!Modifier.isStatic(targetMethod.flags())) {
            throw new IllegalConfigurationException(
                    "`@%s` can be used only on static methods. Offending class is '%s'".formatted(annotationName,
                            aiServiceClassInfo.name()));
        }
        DotName returnType = targetMethod.returnType().name();
        if ((!returnType.equals(DotNames.STRING)) && !returnType.equals(LangChain4jDotNames.TOOL_ERROR_HANDLER_RESULT)) {
            throw new IllegalConfigurationException(
                    "`@%s` can be used only on static methods that return '%s' or '%s'. Offending class is '%s'"
                            .formatted(annotationName,
                                    DotNames.STRING, LangChain4jDotNames.TOOL_ERROR_HANDLER_RESULT, aiServiceClassInfo.name()));
        }

        ClassOutput output = new GeneratedBeanGizmoAdaptor(generatedBean);
        String generatedClassName = aiServiceClassInfo.name().toString() + "$" + annotationName.withoutPackagePrefix();
        ClassCreator.Builder classCreatorBuilder = ClassCreator.builder()
                .classOutput(output)
                .interfaces(interfaceType)
                .className(generatedClassName);
        try (ClassCreator classCreator = classCreatorBuilder.build()) {
            classCreator.addAnnotation(Singleton.class);

            MethodCreator handleMethod = classCreator.getMethodCreator(MethodDescriptor.ofMethod(generatedClassName, "handle",
                    ToolErrorHandlerResult.class, Throwable.class, ToolErrorContext.class));

            List<ResultHandle> paramHandles = new ArrayList<>();
            for (MethodParameterInfo parameter : targetMethod.parameters()) {
                DotName paramTypeDotName = parameter.type().name();
                if (paramTypeDotName.equals(DotNames.THROWABLE) || paramTypeDotName.equals(DotNames.EXCEPTION)) {
                    paramHandles.add(handleMethod.getMethodParam(0));
                } else if (paramTypeDotName.equals(LangChain4jDotNames.TOOL_ERROR_CONTEXT)) {
                    paramHandles.add(handleMethod.getMethodParam(1));
                } else {
                    throw new IllegalConfigurationException(
                            "`@%s` can be used only on static methods that use the parameters of type '%s' or '%s'. Offending class is '%s'"
                                    .formatted(annotationName,
                                            DotNames.THROWABLE, LangChain4jDotNames.TOOL_ERROR_CONTEXT,
                                            aiServiceClassInfo.name()));
                }
            }

            ResultHandle result = handleMethod.invokeStaticInterfaceMethod(targetMethod,
                    paramHandles.toArray(new ResultHandle[0]));
            if (returnType.equals(LangChain4jDotNames.TOOL_ERROR_HANDLER_RESULT)) {
                handleMethod.returnValue(result);
            } else if (returnType.equals(DotNames.STRING)) {
                ResultHandle toolErrorHandlerResultResult = handleMethod.invokeStaticMethod(
                        MethodDescriptor.ofMethod(ToolErrorHandlerResult.class, "text", ToolErrorHandlerResult.class,
                                String.class),
                        result);
                handleMethod.returnValue(toolErrorHandlerResultResult);
            } else {
                throw new IllegalStateException("Unhandled result type: " + returnType);
            }

        }

        return DotName.createSimple(generatedClassName);
    }

    private static List<ClassInfo> tools(AnnotationInstance instance, IndexView index) {
        AnnotationValue toolsInstance = instance.value("tools");
        if (toolsInstance != null) {
            return Arrays.stream(toolsInstance.asClassArray()).map(t -> {
                var ci = index.getClassByName(t.name());
                if (ci == null) {
                    throw new IllegalArgumentException("Cannot find class " + t.name()
                            + " in index. Please make sure it's a valid CDI bean known to Quarkus");
                }
                return ci;
            }).toList();
        }
        return Collections.emptyList();
    }

    // toolHallucinationStrategy helper removed — resolution is now handled by resolveComponent()

    /**
     * Resolves a component attribute value to a DotName and ComponentResolutionMode.
     * Implements the tri-state resolution model:
     * - void.class → SKIP
     * - Interface type matching interfaceType → AUTO_DISCOVER
     * - Concrete class → EXPLICIT
     */
    private record ComponentResolution(DotName className, ComponentResolutionMode mode) {
        static final ComponentResolution SKIP = new ComponentResolution(null, ComponentResolutionMode.SKIP);
    }

    private ComponentResolution resolveComponent(AnnotationValue annotationValue, DotName interfaceType) {
        if (annotationValue == null) {
            return ComponentResolution.SKIP;
        }
        DotName dotName = annotationValue.asClass().name();
        if (VOID_CLASS.equals(dotName)) {
            return ComponentResolution.SKIP;
        }
        if (dotName.equals(interfaceType)) {
            return new ComponentResolution(dotName, ComponentResolutionMode.AUTO_DISCOVER);
        }
        return new ComponentResolution(dotName, ComponentResolutionMode.EXPLICIT);
    }

    private void validateClassExistsAndRegister(DotName classDotName, IndexView index,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer) {
        ClassInfo classInfo = index.getClassByName(classDotName);
        if (classInfo == null) {
            log.warn("'" + classDotName.toString() + "' cannot be indexed");
            return;
        }

        reflectiveClassProducer
                .produce(ReflectiveClassBuildItem.builder(classDotName.toString()).constructors(true).build());
        unremovableBeanProducer.produce(UnremovableBeanBuildItem.beanTypes(classDotName));
    }

    private boolean isImageOrImageResultResult(Type returnType) {
        if (returnType.name().equals(LangChain4jDotNames.IMAGE)) {
            return true;
        } else if (returnType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType parameterizedType = returnType.asParameterizedType();
            if (LangChain4jDotNames.RESULT.equals(parameterizedType.name())
                    && (parameterizedType.arguments().size() == 1)) {
                if (parameterizedType.arguments().get(0).name().equals(LangChain4jDotNames.IMAGE)) {
                    return true;
                }
            }
        }
        return false;
    }

    @BuildStep
    public void toolQualifiers(BuildProducer<ToolQualifierProvider.BuildItem> producer) {
        producer.produce(new ToolQualifierProvider.BuildItem(new ToolQualifierProvider() {
            @Override
            public boolean supports(ClassInfo classInfo) {
                return classInfo.hasAnnotation(DotNames.REGISTER_REST_CLIENT);
            }

            @Override
            public AnnotationLiteral<?> qualifier(ClassInfo classInfo) {
                return new RestClient.RestClientLiteral();
            }
        }));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public void handleDeclarativeServices(AiServicesRecorder recorder,
            List<DeclarativeAiServiceBuildItem> declarativeAiServiceItems,
            List<SelectedChatModelProviderBuildItem> selectedChatModelProvider,
            List<ToolQualifierProvider.BuildItem> toolQualifierProviderItems,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer) {

        boolean needsChatModelBean = false;
        boolean needsStreamingChatModelBean = false;
        boolean needsChatMemoryProviderBean = false;
        boolean needsRetrieverBean = false;
        boolean needsRetrievalAugmentorBean = false;
        boolean needsModerationModelBean = false;
        boolean needsImageModelBean = false;
        boolean needsToolProviderBean = false;
        boolean needsToolSearchStrategyBean = false;
        Set<DotName> allToolNames = new HashSet<>();
        Set<DotName> allToolProviders = new HashSet<>();
        Set<DotName> allToolSearchStrategies = new HashSet<>();
        Set<DotName> allToolHallucinationStrategies = new HashSet<>();

        for (DeclarativeAiServiceBuildItem bi : declarativeAiServiceItems) {
            ClassInfo declarativeAiServiceClassInfo = bi.getServiceClassInfo();
            String serviceClassName = declarativeAiServiceClassInfo.name().toString();
            Integer maxToolCallingRoundTrips = bi.getMaxToolCallingRoundTrips();
            boolean allowContinuousForcedToolCalling = bi.isAllowContinuousForcedToolCalling();

            List<ToolQualifierProvider> toolQualifierProviders = toolQualifierProviderItems.stream().map(
                    ToolQualifierProvider.BuildItem::getProvider).toList();
            Map<String, AnnotationLiteral<?>> toolToQualifierMap = new HashMap<>();
            for (ClassInfo ci : bi.getToolClassInfos()) {
                AnnotationLiteral<?> qualifier = null;
                for (ToolQualifierProvider provider : toolQualifierProviders) {
                    if (provider.supports(ci)) {
                        qualifier = provider.qualifier(ci);
                        break;
                    }
                }
                toolToQualifierMap.put(ci.name().toString(), qualifier);
            }

            // Build ComponentEntry records for each component
            ComponentEntry chatMemoryProviderEntry = toComponentEntry(bi.getChatMemoryProviderClassDotName(),
                    bi.getChatMemoryProviderResolutionMode());
            ComponentEntry chatMemoryFlushStrategyEntry = toComponentEntry(bi.getChatMemoryFlushStrategyClassDotName(),
                    bi.getChatMemoryFlushStrategyResolutionMode());
            ComponentEntry retrievalAugmentorEntry = toComponentEntry(bi.getRetrievalAugmentorClassDotName(),
                    bi.getRetrievalAugmentorResolutionMode());
            ComponentEntry moderationModelEntry = toComponentEntry(bi.getModerationModelClassDotName(),
                    bi.getModerationModelResolutionMode());
            ComponentEntry imageModelEntry = toComponentEntry(bi.getImageModelClassDotName(),
                    bi.getImageModelResolutionMode());
            ComponentEntry toolProviderEntry = toComponentEntry(bi.getToolProviderClassDotName(),
                    bi.getToolProviderResolutionMode());
            ComponentEntry toolSearchStrategyEntry = toComponentEntry(bi.getToolSearchStrategyClassDotName(),
                    bi.getToolSearchStrategyResolutionMode());
            ComponentEntry toolHallucinationStrategyEntry = toComponentEntry(bi.getToolHallucinationStrategyClassDotName(),
                    bi.getToolHallucinationStrategyResolutionMode());
            ComponentEntry systemMessageProviderEntry = toComponentEntry(bi.getSystemMessageProviderClassDotName(),
                    bi.getSystemMessageProviderResolutionMode());

            if (bi.getToolHallucinationStrategyClassDotName() != null) {
                allToolHallucinationStrategies.add(bi.getToolHallucinationStrategyClassDotName());
            }

            String toolArgumentsErrorHandlerDotName = (bi.getToolArgumentsErrorHandlerDotName() != null
                    ? bi.getToolArgumentsErrorHandlerDotName().toString()
                    : null);

            String toolExecutionErrorHandlerDotName = (bi.getToolExecutionErrorHandlerDotName() != null
                    ? bi.getToolExecutionErrorHandlerDotName().toString()
                    : null);

            String chatMemorySeederClassName = (bi.getChatMemorySeederClassDotName() != null
                    ? bi.getChatMemorySeederClassDotName().toString()
                    : null);

            String thinkingHandlerClassName = (bi.getThinkingHandlerClassDotName() != null
                    ? bi.getThinkingHandlerClassDotName().toString()
                    : null);

            String defaultMemoryIdProviderClassName = (bi.getDefaultMemoryIdProviderClassDotName() != null
                    ? bi.getDefaultMemoryIdProviderClassDotName().toString()
                    : null);

            // determine whether the method returns Multi<String>
            boolean injectStreamingChatModelBean = false;
            // currently in one class either streaming or blocking model are supported, but not both
            // if we want to support it, the injectStreamingChatModelBean needs to be recorded per injection point
            for (MethodInfo method : declarativeAiServiceClassInfo.methods()) {
                if (LangChain4jDotNames.TOKEN_STREAM.equals(method.returnType().name())) {
                    injectStreamingChatModelBean = true;
                    continue;
                }

                if (!DotNames.MULTI.equals(method.returnType().name())) {
                    continue;
                }
                boolean isSupportedResponseType = false;
                if (method.returnType().kind() == Type.Kind.PARAMETERIZED_TYPE) {
                    Type multiType = method.returnType().asParameterizedType().arguments().get(0);
                    if (DotNames.STRING.equals(multiType.name())
                            || LangChain4jDotNames.CHAT_EVENT.equals(multiType.name())) {
                        isSupportedResponseType = true;
                    }
                }
                if (!isSupportedResponseType) {
                    throw illegalConfiguration("Only Multi<String> is supported as a Multi return type. Offending method is '"
                            + method.declaringClass().name().toString() + "#" + method.name() + "'");
                }
                injectStreamingChatModelBean = true;
            }

            boolean injectModerationModelBean = false;
            for (MethodInfo method : declarativeAiServiceClassInfo.methods()) {
                if (method.hasAnnotation(Moderate.class)) {
                    injectModerationModelBean = true;
                    break;
                }
            }

            // determine whether the method ImageModel
            boolean injectImageModel = false;
            // currently in one class either streaming or blocking model are supported, but not both
            // if we want to support it, the injectStreamingChatModelBean needs to be recorded per injection point
            for (MethodInfo method : declarativeAiServiceClassInfo.methods()) {
                if (!isImageOrImageResultResult(method.returnType())) {
                    continue;
                }
                injectImageModel = true;
            }

            String chatModelName = bi.getChatModelName();
            String moderationModelName = bi.getModerationModelName();

            // Detect whether the service needs a blocking ChatModel.
            // Default to true: if no methods are declared directly (all inherited), assume blocking is needed.
            boolean needsBlockingModel = true;
            List<MethodInfo> declaredMethods = declarativeAiServiceClassInfo.methods();
            if (!declaredMethods.isEmpty()) {
                needsBlockingModel = false;
                for (MethodInfo method : declaredMethods) {
                    if (!LangChain4jDotNames.TOKEN_STREAM.equals(method.returnType().name())
                            && !DotNames.MULTI.equals(method.returnType().name())) {
                        needsBlockingModel = true;
                        break;
                    }
                }
            }
            boolean injectChatModel = needsBlockingModel || !selectedChatModelProvider.isEmpty();

            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(QuarkusAiServiceContext.class)
                    .unremovable()
                    .forceApplicationClass()
                    .createWith(recorder.createDeclarativeAiService(
                            new DeclarativeAiServiceCreateInfo(
                                    serviceClassName,
                                    toolToQualifierMap,
                                    chatMemoryProviderEntry,
                                    chatMemoryFlushStrategyEntry,
                                    retrievalAugmentorEntry,
                                    moderationModelEntry,
                                    imageModelEntry,
                                    toolProviderEntry,
                                    toolSearchStrategyEntry,
                                    toolHallucinationStrategyEntry,
                                    systemMessageProviderEntry,
                                    chatMemorySeederClassName,
                                    thinkingHandlerClassName,
                                    chatModelName,
                                    moderationModelName,
                                    bi.getImageModelName(),
                                    injectChatModel,
                                    injectStreamingChatModelBean,
                                    injectModerationModelBean,
                                    injectImageModel,
                                    toolArgumentsErrorHandlerDotName,
                                    toolExecutionErrorHandlerDotName,
                                    classInputGuardrails(bi),
                                    classOutputGuardrails(bi),
                                    maxToolCallingRoundTrips,
                                    bi.getMaxToolCallsPerResponse(),
                                    allowContinuousForcedToolCalling,
                                    bi.isShouldThrowExceptionOnEventError(),
                                    defaultMemoryIdProviderClassName)))
                    .setRuntimeInit()
                    .addQualifier()
                    .annotation(LangChain4jDotNames.QUARKUS_AI_SERVICE_CONTEXT_QUALIFIER).addValue("value", serviceClassName)
                    .done()
                    .scope(Dependent.class);

            // Model injection — via CDI.
            boolean injectStreamingModel = injectStreamingChatModelBean;

            if (injectChatModel) {
                if (NamedConfigUtil.isDefault(chatModelName)) {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.CHAT_MODEL));
                } else {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.CHAT_MODEL),
                            AnnotationInstance.builder(ModelName.class).add("value", chatModelName).build());
                }
                needsChatModelBean = true;
            }
            if (injectStreamingModel) {
                if (NamedConfigUtil.isDefault(chatModelName)) {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.STREAMING_CHAT_MODEL));
                } else {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.STREAMING_CHAT_MODEL),
                            AnnotationInstance.builder(ModelName.class).add("value", chatModelName).build());
                }
                needsStreamingChatModelBean = true;
            }

            for (var entry : toolToQualifierMap.entrySet()) {
                DotName dotName = DotName.createSimple(entry.getKey());
                AnnotationLiteral<?> qualifier = entry.getValue();
                if (qualifier == null) {
                    configurator.addInjectionPoint(ClassType.create(dotName));
                } else {
                    configurator.addInjectionPoint(ClassType.create(dotName),
                            AnnotationInstance.builder(qualifier.annotationType()).build());
                }
                allToolNames.add(dotName);
            }

            if (bi.getToolHallucinationStrategyClassDotName() != null) {
                configurator.addInjectionPoint(ClassType.create(bi.getToolHallucinationStrategyClassDotName()));
            }
            if (bi.getToolArgumentsErrorHandlerDotName() != null) {
                configurator.addInjectionPoint(ClassType.create(bi.getToolArgumentsErrorHandlerDotName()));
            }
            if (bi.getToolExecutionErrorHandlerDotName() != null) {
                configurator.addInjectionPoint(ClassType.create(bi.getToolExecutionErrorHandlerDotName()));
            }

            // Chat memory provider injection
            switch (bi.getChatMemoryProviderResolutionMode()) {
                case AUTO_DISCOVER -> {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.CHAT_MEMORY_PROVIDER));
                    needsChatMemoryProviderBean = true;
                }
                case EXPLICIT -> {
                    configurator.addInjectionPoint(ClassType.create(bi.getChatMemoryProviderClassDotName()));
                    unremovableProducer.produce(
                            UnremovableBeanBuildItem.beanTypes(bi.getChatMemoryProviderClassDotName()));
                }
                case SKIP -> {
                }
            }

            // Chat memory flush strategy injection
            if (bi.getChatMemoryFlushStrategyResolutionMode() == ComponentResolutionMode.EXPLICIT) {
                configurator.addInjectionPoint(ClassType.create(bi.getChatMemoryFlushStrategyClassDotName()));
                unremovableProducer.produce(
                        UnremovableBeanBuildItem.beanTypes(bi.getChatMemoryFlushStrategyClassDotName()));
            }

            // System message provider injection
            if (bi.getSystemMessageProviderClassDotName() != null
                    && bi.getSystemMessageProviderResolutionMode() == ComponentResolutionMode.EXPLICIT) {
                configurator.addInjectionPoint(ClassType.create(bi.getSystemMessageProviderClassDotName()));
                unremovableProducer.produce(
                        UnremovableBeanBuildItem.beanTypes(bi.getSystemMessageProviderClassDotName()));
            }

            // Retrieval augmentor injection
            switch (bi.getRetrievalAugmentorResolutionMode()) {
                case AUTO_DISCOVER -> {
                    configurator.addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(LangChain4jDotNames.RETRIEVAL_AUGMENTOR) }, null));
                    needsRetrievalAugmentorBean = true;
                }
                case EXPLICIT -> {
                    configurator.addInjectionPoint(ClassType.create(bi.getRetrievalAugmentorClassDotName()));
                    unremovableProducer.produce(
                            UnremovableBeanBuildItem.beanClassNames(bi.getRetrievalAugmentorClassDotName().toString()));
                }
                case SKIP -> {
                }
            }

            // Moderation model injection
            if (bi.getModerationModelResolutionMode() != ComponentResolutionMode.SKIP && injectModerationModelBean) {
                if (NamedConfigUtil.isDefault(moderationModelName)) {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.MODERATION_MODEL));
                } else {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.MODERATION_MODEL),
                            AnnotationInstance.builder(ModelName.class).add("value", moderationModelName).build());
                }
                needsModerationModelBean = true;
            }

            // Image model injection
            if (bi.getImageModelResolutionMode() != ComponentResolutionMode.SKIP && injectImageModel) {
                if (NamedConfigUtil.isDefault(chatModelName)) {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.IMAGE_MODEL));
                } else {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.IMAGE_MODEL),
                            AnnotationInstance.builder(ModelName.class).add("value", chatModelName).build());
                }
                needsImageModelBean = true;
            }

            // Tool provider injection
            switch (bi.getToolProviderResolutionMode()) {
                case AUTO_DISCOVER -> {
                    configurator.addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(LangChain4jDotNames.TOOL_PROVIDER) }, null));
                    needsToolProviderBean = true;
                }
                case EXPLICIT -> {
                    DotName toolProvider = bi.getToolProviderClassDotName();
                    configurator.addInjectionPoint(ClassType.create(toolProvider));
                    allToolProviders.add(toolProvider);
                }
                case SKIP -> {
                }
            }

            // Tool search strategy injection
            switch (bi.getToolSearchStrategyResolutionMode()) {
                case AUTO_DISCOVER -> {
                    configurator.addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(LangChain4jDotNames.TOOL_SEARCH_STRATEGY) }, null));
                    needsToolSearchStrategyBean = true;
                }
                case EXPLICIT -> {
                    DotName toolSearchStrategy = bi.getToolSearchStrategyClassDotName();
                    configurator.addInjectionPoint(ClassType.create(toolSearchStrategy));
                    allToolSearchStrategies.add(toolSearchStrategy);
                }
                case SKIP -> {
                }
            }

            configurator
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(OutputGuardrail.class) }, null))
                    .done();

            syntheticBeanProducer.produce(configurator.done());
        }

        if (needsChatModelBean) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(LangChain4jDotNames.CHAT_MODEL));
        }
        if (needsStreamingChatModelBean) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(LangChain4jDotNames.STREAMING_CHAT_MODEL));
        }
        if (needsChatMemoryProviderBean) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(LangChain4jDotNames.CHAT_MEMORY_PROVIDER));
        }
        if (needsRetrieverBean) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(LangChain4jDotNames.RETRIEVER));
        }
        if (needsRetrievalAugmentorBean) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(LangChain4jDotNames.RETRIEVAL_AUGMENTOR));
        }
        if (needsModerationModelBean) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(LangChain4jDotNames.MODERATION_MODEL));
        }
        if (needsImageModelBean) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(LangChain4jDotNames.IMAGE_MODEL));
        }
        if (needsToolProviderBean) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(LangChain4jDotNames.TOOL_PROVIDER));
        }
        if (!allToolProviders.isEmpty()) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(allToolProviders));
        }
        if (needsToolSearchStrategyBean) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(LangChain4jDotNames.TOOL_SEARCH_STRATEGY));
        }
        if (!allToolSearchStrategies.isEmpty()) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(allToolSearchStrategies));
        }
        if (!allToolNames.isEmpty()) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(allToolNames));
        }
        if (!allToolHallucinationStrategies.isEmpty()) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(allToolHallucinationStrategies));
        }
    }

    private static ComponentEntry toComponentEntry(DotName className, ComponentResolutionMode mode) {
        if (mode == ComponentResolutionMode.SKIP) {
            return ComponentEntry.SKIP;
        }
        return new ComponentEntry(className != null ? className.toString() : null, mode);
    }

    @BuildStep
    public void markUsedGuardRailsUnremovable(List<AiServicesMethodBuildItem> methods,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer) {
        for (AiServicesMethodBuildItem method : methods) {
            List<String> list = new ArrayList<>();

            method.getInputGuardrails()
                    .map(InputGuardrailsLiteral::value)
                    .map(Arrays::stream)
                    .orElseGet(Stream::of)
                    .map(Class::getName)
                    .forEach(list::add);

            method.getOutputGuardrails()
                    .map(OutputGuardrailsLiteral::value)
                    .map(Arrays::stream)
                    .orElseGet(Stream::of)
                    .map(Class::getName)
                    .forEach(list::add);

            for (String cn : list) {
                unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(DotName.createSimple(cn)));
            }
            if (method.getMethodInfo().hasAnnotation(DotNames.OUTPUT_GUARDRAIL_ACCUMULATOR)) {
                DotName name = method.getMethodInfo().annotation(DotNames.OUTPUT_GUARDRAIL_ACCUMULATOR)
                        .value().asClass().name();
                unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(name));
            }
        }
    }

    @BuildStep
    public void markUsedResponseAugmenterUnremovable(List<AiServicesMethodBuildItem> methods,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer) {
        for (AiServicesMethodBuildItem method : methods) {
            var cn = method.getResponseAugmenter();
            if (cn != null) {
                unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(DotName.createSimple(cn)));
            }
        }
    }

    /**
     * Because the tools execution uses an imperative API (`String execute(...)`) and uses the caller thread, we need
     * to anticipate the need to dispatch the invocation on a worker thread.
     * This is the case for AI service methods that returns `Uni`, `CompletionStage` and `Multi` (stream) and that uses
     * tools returning `Uni`, `CompletionStage` or that are blocking.
     * Basically, for "reactive AI service method, the switch is necessary except if all the tools are imperative (return `T`)
     * and marked explicitly as blocking (using `@Blocking`).
     *
     * @param method the AI method
     * @param tools the tools
     * @param toolProviderClassDotName the tool provider class name (if configured)
     */
    public boolean detectAiServiceMethodThanNeedToBeDispatchedOnWorkerThread(
            MethodInfo method,
            List<String> associatedTools,
            List<ToolMethodBuildItem> tools,
            DotName toolProviderClassDotName,
            List<String> mcpClientNames,
            IndexView index) {
        boolean reactive = method.returnType().name().equals(DotNames.UNI)
                || method.returnType().name().equals(DotNames.COMPLETION_STAGE)
                || method.returnType().name().equals(DotNames.MULTI);

        boolean requireSwitchToWorkerThread = false;

        if (!reactive) {
            // We are already on a thread we can block.
            return false;
        }

        if (mcpClientNames != null) {
            // MCP clients are blocking for now, so we need to switch to a worker thread
            // note: null means no MCP clients, empty list means that there is a McpToolBox annotation that takes all
            // MCP clients
            return true;
        }

        // If a ToolProvider is configured for a reactive method, assume it may provide blocking tools at runtime
        if (toolProviderClassDotName != null) {
            return true;
        }

        if (associatedTools.isEmpty()) {
            // No tools, no need to dispatch
            return false;
        }

        // We need to find if any of the tools that could be used by the method is requiring a blocking execution
        for (String classname : associatedTools) {
            // Look for the tool in the list of tools (check class hierarchy for inherited tools)
            boolean found = false;
            for (ToolMethodBuildItem tool : tools) {
                if (isToolDeclaredInHierarchy(tool.getDeclaringClassName(), classname, index)) {
                    found = true;
                    if (tool.requiresSwitchToWorkerThread()) {
                        requireSwitchToWorkerThread = true;
                        break;
                    }
                }
            }
            if (!found) {
                throw new RuntimeException("No tools detected in " + classname);
            }
        }
        return requireSwitchToWorkerThread;
    }

    private static boolean isToolDeclaredInHierarchy(String declaringClassName, String toolClassName, IndexView index) {
        if (declaringClassName.equals(toolClassName)) {
            return true;
        }
        DotName declaringDotName = DotName.createSimple(declaringClassName);
        ClassInfo declaringClass = index.getClassByName(declaringDotName);
        if (declaringClass == null) {
            return false;
        }
        Collection<ClassInfo> descendants;
        if (declaringClass.isInterface()) {
            descendants = index.getAllKnownImplementors(declaringDotName);
        } else {
            descendants = index.getAllKnownSubclasses(declaringDotName);
        }
        return descendants.stream().anyMatch(ci -> ci.name().toString().equals(toolClassName));
    }

    @BuildStep
    public void validateGuardrails(SynthesisFinishedBuildItem synthesisFinished,
            List<AiServicesMethodBuildItem> methods,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {

        for (AiServicesMethodBuildItem method : methods) {
            List<String> list = new ArrayList<>();

            method.getInputGuardrails()
                    .map(InputGuardrailsLiteral::value)
                    .map(Arrays::stream)
                    .orElseGet(Stream::of)
                    .map(Class::getName)
                    .forEach(list::add);

            method.getOutputGuardrails()
                    .map(OutputGuardrailsLiteral::value)
                    .map(Arrays::stream)
                    .orElseGet(Stream::of)
                    .map(Class::getName)
                    .forEach(list::add);

            for (String cn : list) {
                if (synthesisFinished.beanStream().withBeanType(DotName.createSimple(cn)).isEmpty()) {
                    errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new DeploymentException("Missing guardrail bean: " + cn)));
                }
            }

            DotName dotName = DotName.createSimple(OutputGuardrailAccumulator.class);
            if (method.getMethodInfo().hasAnnotation(dotName)) {
                // We have an accumulator
                // Check that the accumulator exists
                var bean = method.getMethodInfo().annotation(dotName).value().asClass().name();
                if (synthesisFinished.beanStream().withBeanType(bean).isEmpty()) {
                    errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new DeploymentException("Missing accumulator bean: " + bean.toString())));
                }

                // Check that the accumulator is used on a method retuning a Multi
                DotName returnedType = method.getMethodInfo().returnType().name();
                if (!DotName.createSimple(Multi.class).equals(returnedType)) {
                    errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new DeploymentException("OutputGuardrailAccumulator can only be used on method returning a " +
                                    "`Multi<X>`: found `%s` for method `%s.%s`".formatted(returnedType,
                                            method.getMethodInfo().declaringClass().toString(),
                                            method.getMethodInfo().name()))));
                }

                // Check that the method has output guardrails
                if (!method.hasOutputGuardrails()) {
                    errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new DeploymentException(
                                    "OutputGuardrailAccumulator used without dev.langchain4j.service.guardrail.OutputGuardrails in method `%s.%s`"
                                            .formatted(method.getMethodInfo().declaringClass().toString(),
                                                    method.getMethodInfo().name()))));
                }
            }
        }
    }

    @BuildStep
    public void defaultToolExecutionErrorHandler(CombinedIndexBuildItem indexBuildItem,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {
        IndexView index = indexBuildItem.getIndex();
        List<AnnotationInstance> instances = new ArrayList<>();
        instances.addAll(index.getAnnotations(LangChain4jDotNames.DEFAULT_TOOL_EXECUTION_ERROR_HANDLER));
        if (instances.isEmpty()) {
            return;
        } else if (instances.size() > 1) {
            throw new IllegalStateException(
                    "Multiple @DefaultToolExecutionErrorHandler annotations found.  Only one is allowed.");
        }
        String className = instances.get(0).target().asClass().name().toString();
        additionalBeanProducer.produce(
                AdditionalBeanBuildItem.builder().addBeanClass(className).setDefaultScope(SINGLETON).setUnremovable().build());
    }

    @BuildStep
    public void watchResourceFiles(CombinedIndexBuildItem indexBuildItem,
            BuildProducer<HotDeploymentWatchedFileBuildItem> producer) {
        IndexView index = indexBuildItem.getIndex();
        List<AnnotationInstance> instances = new ArrayList<>();
        instances.addAll(index.getAnnotations(LangChain4jDotNames.SYSTEM_MESSAGE));
        instances.addAll(index.getAnnotations(LangChain4jDotNames.USER_MESSAGE));

        for (AnnotationInstance instance : instances) {
            AnnotationValue fromResource = instance.value("fromResource");
            if (fromResource != null) {
                producer.produce(new HotDeploymentWatchedFileBuildItem(fromResource.asString()));
            }
        }
    }

    @BuildStep
    public MethodParameterAllowedAnnotationsBuildItem markMemoryIdAsAllowedAnnotation() {
        return new MethodParameterAllowedAnnotationsBuildItem(anno -> MEMORY_ID.equals(anno.name()));
    }

    @BuildStep
    public void markIgnoredAnnotations(BuildProducer<MethodParameterIgnoredAnnotationsBuildItem> producer) {
        producer.produce(new MethodParameterIgnoredAnnotationsBuildItem(dotname -> {
            return dotname.name().toString().startsWith("kotlin");
        }));
        producer.produce(new MethodParameterIgnoredAnnotationsBuildItem(dotname -> {
            return dotname.name().toString().startsWith("jakarta.validation.constraints");
        }));
        producer.produce(new MethodParameterIgnoredAnnotationsBuildItem(dotname -> {
            return dotname.name().toString().endsWith("NotNull");
        }));
        producer.produce(new MethodParameterIgnoredAnnotationsBuildItem(dotname -> {
            return dotname.name().toString().startsWith("io.opentelemetry");
        }));
    }

    @BuildStep
    AnnotationsImpliesAiServiceBuildItem implyAiService() {
        return new AnnotationsImpliesAiServiceBuildItem(
                List.of(LangChain4jDotNames.SYSTEM_MESSAGE, LangChain4jDotNames.USER_MESSAGE,
                        LangChain4jDotNames.MODERATE));
    }

    @BuildStep
    public void annotationTransformations(BuildProducer<AnnotationsTransformerBuildItem> producer) {

    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public void handleAiServices(
            LangChain4jBuildConfig config,
            AiServicesRecorder recorder,
            RecorderContext recorderContext,
            CombinedIndexBuildItem indexBuildItem,
            CurateOutcomeBuildItem curateOutcomeBuildItem,
            List<DeclarativeAiServiceBuildItem> declarativeAiServiceItems,
            List<MethodParameterAllowedAnnotationsBuildItem> methodParameterAllowedAnnotationsItems,
            List<MethodParameterIgnoredAnnotationsBuildItem> methodParameterIgnoredAnnotationsItems,
            BuildProducer<GeneratedClassBuildItem> generatedClassProducer,
            BuildProducer<GeneratedBeanBuildItem> generatedBeanProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<AiServicesMethodBuildItem> aiServicesMethodProducer,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer,
            Optional<MetricsCapabilityBuildItem> metricsCapability,
            Capabilities capabilities,
            List<ToolMethodBuildItem> tools,
            List<ToolQualifierProvider.BuildItem> toolQualifierProviderItems,
            List<AnnotationsImpliesAiServiceBuildItem> annotationsImpliesAiServiceItems,
            List<SkipOutputFormatInstructionsBuildItem> skipOutputFormatInstructionsItems,
            List<FallbackToDummyUserMessageBuildItem> fallbackToDummyUserMessageItems) {

        IndexView index = indexBuildItem.getIndex();

        List<AiServicesUseAnalyzer.Result.Entry> aiServicesAnalysisResults = new ArrayList<>();
        for (ClassInfo classInfo : index.getKnownUsers(LangChain4jDotNames.AI_SERVICES)) {
            String className = classInfo.name().toString();
            if (className.startsWith("io.quarkiverse.langchain4j") || className.startsWith("dev.langchain4j")) { // TODO: this can be made smarter if
                // needed
                continue;
            }
            try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(
                    className.replace('.', '/') + ".class")) {
                if (is == null) {
                    return;
                }
                var cn = new ClassNode(Gizmo.ASM_API_VERSION);
                var cr = new ClassReader(is);
                cr.accept(cn, 0);
                for (MethodNode method : cn.methods) {
                    aiServicesAnalysisResults.addAll(AiServicesUseAnalyzer.analyze(cn, method).entries);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Reading bytecode of class '" + className + "' failed", e);
            } catch (AnalyzerException e) {
                log.debug("Unable to analyze bytecode of class '" + className + "'", e);
            }
        }
        Map<String, Boolean> nameToUsed = aiServicesAnalysisResults.stream()
                .collect(Collectors.toMap(e -> e.createdClassName, e -> e.chatMemoryProviderUsed, (u1, u2) -> u1 || u2));
        for (var entry : nameToUsed.entrySet()) {
            String className = entry.getKey();
            ClassInfo classInfo = index.getClassByName(className);
            if (classInfo == null) {
                continue;
            }
            if (!classInfo.annotations(LangChain4jDotNames.MEMORY_ID).isEmpty() && !entry.getValue()) {
                log.warn("Class '" + className
                        + "' is used in AiServices and while it leverages @MemoryId, a ChatMemoryProvider has not been configured. This will likely result in an exception being thrown when the service is used.");
            }
        }

        Set<String> detectedForCreate = new HashSet<>(nameToUsed.keySet());
        addCreatedAware(index, detectedForCreate);
        addIfacesWithMessageAnns(index, annotationsImpliesAiServiceItems.stream()
                .flatMap(bi -> bi.getAnnotationNames().stream()).collect(Collectors.toList()), detectedForCreate);
        Set<String> registeredAiServiceClassNames = declarativeAiServiceItems.stream()
                .map(bi -> bi.getServiceClassInfo().name().toString()).collect(
                        Collectors.toUnmodifiableSet());
        detectedForCreate.addAll(registeredAiServiceClassNames);

        Set<ClassInfo> ifacesForCreate = new HashSet<>();
        for (String className : detectedForCreate) {
            ClassInfo classInfo = index.getClassByName(className);
            if (classInfo == null) {
                log.warn("'" + className
                        + "' used for creating an AiService was not found in the Quarkus index. Attempting to create "
                        + "an AiService using this class will fail");
                continue;
            }
            if (!classInfo.isInterface()) {
                log.warn("'" + className
                        + "' used for creating an AiService is not an interface. Attempting to create an AiService "
                        + "using this class will fail");
            }

            ifacesForCreate.add(classInfo);
        }

        var addMicrometerMetrics = metricsCapability.isPresent()
                && metricsCapability.get().metricsSupported(MetricsFactory.MICROMETER);
        if (addMicrometerMetrics) {
            additionalBeanProducer.produce(AdditionalBeanBuildItem.builder().addBeanClass(MetricsTimedWrapper.class).build());
            additionalBeanProducer.produce(AdditionalBeanBuildItem.builder().addBeanClass(MetricsCountedWrapper.class).build());
        }

        var addOpenTelemetrySpan = capabilities.isPresent(Capability.OPENTELEMETRY_TRACER);
        if (addOpenTelemetrySpan) {
            additionalBeanProducer.produce(AdditionalBeanBuildItem.builder().addBeanClass(SpanWrapper.class).build());
        }

        Map<String, AiServiceClassCreateInfo> perClassMetadata = new HashMap<>();
        if (!ifacesForCreate.isEmpty()) {
            ClassOutput generatedClassOutput = new GeneratedClassGizmoAdaptor(generatedClassProducer, true);
            ClassOutput generatedBeanOutput = new GeneratedBeanGizmoAdaptor(generatedBeanProducer);
            for (ClassInfo iface : ifacesForCreate) {
                List<MethodInfo> allMethods = new ArrayList<>(iface.methods());
                JandexUtil.getAllSuperinterfaces(iface, index).stream().filter(ci -> !ci.name().equals(
                        CHAT_MEMORY_ACCESS)).forEach(ci -> allMethods.addAll(ci.methods()));

                List<MethodInfo> methodsToImplement = new ArrayList<>();
                Map<String, AiServiceMethodCreateInfo> perMethodMetadata = new HashMap<>();
                for (MethodInfo method : allMethods) {
                    short modifiers = method.flags();
                    if (Modifier.isStatic(modifiers) || Modifier.isPrivate(modifiers) || JandexUtil.isDefault(
                            modifiers)) {
                        continue;
                    }

                    if (methodsToImplement.stream().anyMatch(m -> MethodUtil.methodSignaturesMatch(m, method))) {
                        continue;
                    }
                    methodsToImplement.add(method);
                }

                String ifaceName = iface.name().toString();
                String implClassName = ifaceName + "$$QuarkusImpl";
                boolean isRegisteredService = registeredAiServiceClassNames.contains(ifaceName);

                ClassCreator.Builder classCreatorBuilder = ClassCreator.builder()
                        .classOutput(isRegisteredService ? generatedBeanOutput : generatedClassOutput)
                        .className(implClassName)
                        .interfaces(ifaceName, ChatMemoryRemovable.class.getName(), ChatMemoryAccess.class.getName());
                if (isRegisteredService) {
                    classCreatorBuilder.interfaces(AutoCloseable.class);
                }
                try (ClassCreator classCreator = classCreatorBuilder.build()) {
                    DeclarativeAiServiceBuildItem matchingBI = null;
                    if (isRegisteredService) {
                        // we need to make this a bean, so we need to add the proper scope annotation
                        matchingBI = declarativeAiServiceItems.stream()
                                .filter(bi -> bi.getServiceClassInfo().equals(iface))
                                .findFirst().orElseThrow(() -> new IllegalStateException(
                                        "Unable to determine the CDI scope of " + iface));
                        DotName scopeInfo = matchingBI.getCdiScope();
                        classCreator.addAnnotation(scopeInfo.toString());
                        // copy class level interceptor binding annotations
                        for (AnnotationInstance annotationInstance : iface.declaredAnnotations()) {
                            if (shouldCopyAnnotation(annotationInstance, index)) {
                                classCreator.addAnnotation(annotationInstance);
                            }
                        }
                        if (matchingBI.getBeanName().isPresent()) {
                            classCreator.addAnnotation(
                                    AnnotationInstance.builder(NAMED).add("value", matchingBI.getBeanName().get()).build());
                        }
                        if (matchingBI.isMakeDefaultBean()) {
                            classCreator.addAnnotation(DefaultBean.class);
                        }
                    }

                    FieldDescriptor contextField = classCreator.getFieldCreator("context", QuarkusAiServiceContext.class)
                            .setModifiers(Modifier.PRIVATE | Modifier.FINAL)
                            .getFieldDescriptor();

                    {
                        MethodCreator ctor = classCreator.getMethodCreator(MethodDescriptor.INIT, "V",
                                QuarkusAiServiceContext.class);
                        ctor.setModifiers(Modifier.PUBLIC);
                        ctor.addAnnotation(Inject.class);
                        ctor.getParameterAnnotations(0)
                                .addAnnotation(LangChain4jDotNames.QUARKUS_AI_SERVICE_CONTEXT_QUALIFIER.toString())
                                .add("value", ifaceName);
                        ctor.invokeSpecialMethod(OBJECT_CONSTRUCTOR, ctor.getThis());
                        ctor.writeInstanceField(contextField, ctor.getThis(),
                                ctor.getMethodParam(0));
                        ctor.returnValue(null);
                    }

                    {
                        MethodCreator noArgsCtor = classCreator.getMethodCreator(MethodDescriptor.INIT, "V");
                        noArgsCtor.setModifiers(Modifier.PUBLIC);
                        noArgsCtor.invokeSpecialMethod(OBJECT_CONSTRUCTOR, noArgsCtor.getThis());
                        noArgsCtor.writeInstanceField(contextField, noArgsCtor.getThis(), noArgsCtor.loadNull());
                        noArgsCtor.returnValue(null);
                    }

                    for (MethodInfo methodInfo : methodsToImplement) {
                        // The implementation essentially gets the context and delegates to
                        // MethodImplementationSupport#implement

                        String methodId = createMethodId(methodInfo);
                        Collection<Predicate<AnnotationInstance>> allowedPredicates = methodParameterAllowedAnnotationsItems
                                .stream()
                                .map(item -> item.getPredicate())
                                .collect(Collectors.toList());
                        Collection<Predicate<AnnotationInstance>> ignoredPredicates = methodParameterIgnoredAnnotationsItems
                                .stream()
                                .map(item -> item.getPredicate())
                                .collect(Collectors.toList());
                        AiServiceMethodCreateInfo methodCreateInfo = gatherMethodMetadata(methodInfo, index,
                                addMicrometerMetrics,
                                addOpenTelemetrySpan,
                                config.responseSchema(),
                                allowedPredicates,
                                ignoredPredicates,
                                tools, toolQualifierProviderItems,
                                skipOutputFormatInstructionsItems.stream().map(
                                        SkipOutputFormatInstructionsBuildItem::getPredicate)
                                        .reduce(mi -> false, Predicate::or),
                                fallbackToDummyUserMessageItems.stream().map(
                                        FallbackToDummyUserMessageBuildItem::getPredicate)
                                        .reduce(mi -> false, Predicate::or));
                        if (!methodCreateInfo.getToolClassInfo().isEmpty()) {
                            if ((matchingBI != null)
                                    && matchingBI.getChatMemoryProviderResolutionMode() == ComponentResolutionMode.SKIP) {
                                throw new IllegalArgumentException("Tool usage requires chat memory. Offending AiService is '"
                                        + matchingBI.getServiceClassInfo().name() + "'");
                            }
                            ToolProcessor.warnAboutMissingDeps(curateOutcomeBuildItem,
                                    methodCreateInfo.getToolClassInfo().keySet());

                            methodCreateInfo.getToolClassInfo().keySet().stream()
                                    .map(DotName::createSimple)
                                    .map(UnremovableBeanBuildItem::beanTypes)
                                    .forEach(unremovableBeanProducer::produce);
                        }
                        perMethodMetadata.put(methodId, methodCreateInfo);

                        { // actual method we need to implement
                            MethodCreator mc = classCreator.getMethodCreator(MethodDescriptor.of(methodInfo));

                            // copy annotations
                            for (AnnotationInstance annotationInstance : methodInfo.declaredAnnotations()) {
                                // TODO: we need to review this
                                if (shouldCopyAnnotation(annotationInstance, index)) {
                                    mc.addAnnotation(annotationInstance);
                                }
                            }

                            ResultHandle contextHandle = mc.readInstanceField(contextField, mc.getThis());
                            ResultHandle methodCreateInfoHandle = mc.invokeStaticMethod(RECORDER_METHOD_CREATE_INFO,
                                    mc.load(ifaceName),
                                    mc.load(methodId));
                            ResultHandle paramsHandle = mc.newArray(Object.class, methodInfo.parametersCount());
                            for (int i = 0; i < methodInfo.parametersCount(); i++) {
                                mc.writeArrayValue(paramsHandle, i, mc.getMethodParam(i));
                            }

                            ResultHandle supportHandle = getFromCDI(mc, AiServiceMethodImplementationSupport.class.getName());
                            ResultHandle inputHandle = mc.newInstance(
                                    MethodDescriptor.ofConstructor(AiServiceMethodImplementationSupport.Input.class,
                                            QuarkusAiServiceContext.class, AiServiceMethodCreateInfo.class,
                                            Object[].class),
                                    contextHandle, methodCreateInfoHandle, paramsHandle);

                            ResultHandle resultHandle = mc.invokeVirtualMethod(SUPPORT_IMPLEMENT, supportHandle, inputHandle);
                            mc.returnValue(resultHandle);

                            aiServicesMethodProducer.produce(new AiServicesMethodBuildItem(methodInfo,
                                    methodCreateInfo.getInputGuardrails(),
                                    methodCreateInfo.getOutputGuardrails(),
                                    methodCreateInfo.getResponseAugmenterClassName(),
                                    methodCreateInfo));
                        }
                    }

                    if (isRegisteredService) {
                        MethodCreator mc = classCreator.getMethodCreator(
                                MethodDescriptor.ofMethod(implClassName, "close", void.class));
                        mc.addAnnotation(PreDestroy.class);
                        ResultHandle contextHandle = mc.readInstanceField(contextField, mc.getThis());
                        mc.invokeVirtualMethod(QUARKUS_AI_SERVICES_CONTEXT_CLOSE, contextHandle);
                        mc.returnVoid();
                    }

                    // methods from ChatMemoryRemovable
                    {
                        MethodCreator mc = classCreator.getMethodCreator(
                                MethodDescriptor.ofMethod(implClassName, "remove", void.class, Object[].class));
                        ResultHandle contextHandle = mc.readInstanceField(contextField, mc.getThis());
                        mc.invokeVirtualMethod(QUARKUS_AI_SERVICES_CONTEXT_REMOVE_CHAT_MEMORY_IDS, contextHandle,
                                mc.getMethodParam(0));
                        mc.returnVoid();
                    }
                    // methods from ChatMemoryRemovable.removeAll()
                    {
                        MethodCreator mc = classCreator.getMethodCreator(
                                MethodDescriptor.ofMethod(implClassName, "removeAll", void.class));
                        ResultHandle contextHandle = mc.readInstanceField(contextField, mc.getThis());
                        mc.invokeVirtualMethod(QUARKUS_AI_SERVICES_CONTEXT_CLEAR_CHAT_MEMORY, contextHandle);
                        mc.returnVoid();
                    }
                    // methods from ChatMemoryRemovable.getAllChatMemoryIds()
                    {
                        MethodCreator mc = classCreator.getMethodCreator(
                                MethodDescriptor.ofMethod(implClassName, "getAllChatMemoryIds", Collection.class));
                        ResultHandle contextHandle = mc.readInstanceField(contextField, mc.getThis());
                        ResultHandle result = mc.invokeVirtualMethod(
                                QUARKUS_AI_SERVICES_CONTEXT_GET_ALL_CHAT_MEMORY_IDS,
                                contextHandle);
                        mc.returnValue(result);
                    }

                    // methods from ChatMemoryAccess
                    {
                        MethodCreator mc = classCreator.getMethodCreator(
                                MethodDescriptor.ofMethod(implClassName, "evictChatMemory", boolean.class, Object.class));
                        ResultHandle contextHandle = mc.readInstanceField(contextField, mc.getThis());
                        ResultHandle result = mc.invokeVirtualMethod(QUARKUS_AI_SERVICES_CONTEXT_EVICT_CHAT_MEMORY,
                                contextHandle,
                                mc.getMethodParam(0));
                        mc.returnValue(result);
                    }
                    {
                        MethodCreator mc = classCreator.getMethodCreator(
                                MethodDescriptor.ofMethod(implClassName, "getChatMemory", ChatMemory.class, Object.class));
                        ResultHandle contextHandle = mc.readInstanceField(contextField, mc.getThis());
                        ResultHandle result = mc.invokeVirtualMethod(QUARKUS_AI_SERVICES_CONTEXT_GET_CHAT_MEMORY, contextHandle,
                                mc.getMethodParam(0));
                        mc.returnValue(result);
                    }

                }

                var aiServiceBuildItem = declarativeAiServiceItems.stream()
                        .filter(bi -> bi.getServiceClassInfo().equals(iface))
                        .findFirst();

                var inputGuardrails = aiServiceBuildItem
                        .map(AiServicesProcessor::classInputGuardrails)
                        .orElse(null);

                var outputGuardrails = aiServiceBuildItem
                        .map(AiServicesProcessor::classOutputGuardrails)
                        .orElse(null);

                perClassMetadata.put(ifaceName,
                        new AiServiceClassCreateInfo(perMethodMetadata, implClassName, inputGuardrails, outputGuardrails));
                // make the constructor accessible reflectively since that is how we create the instance
                reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(implClassName).build());
            }

        }

        registerJsonSchema(recorderContext);
        recorder.setMetadata(perClassMetadata);
    }

    private boolean shouldCopyAnnotation(AnnotationInstance annotationInstance, IndexView index) {
        return hasInterceptorBinding(annotationInstance, index);
    }

    private boolean hasInterceptorBinding(AnnotationInstance annotationInstance, IndexView index) {
        DotName annotationDotName = annotationInstance.name();
        ClassInfo annotationClassInfo = index.getClassByName(annotationDotName);
        if (annotationClassInfo != null) {
            return annotationClassInfo.declaredAnnotation(InterceptorBinding.class) != null;
        } else {
            // fallback to loading the annotation
            try {
                Class<?> annotationClass = Class.forName(annotationDotName.toString(), false,
                        Thread.currentThread().getContextClassLoader());
                if (annotationClass.isAnnotationPresent(InterceptorBinding.class)) {
                    return true;
                }
            } catch (ClassNotFoundException ignored) {

            }
        }
        return false;
    }

    private ResultHandle getFromCDI(MethodCreator mc, String className) {
        ResultHandle containerHandle = mc
                .invokeStaticMethod(MethodDescriptor.ofMethod(Arc.class, "container", ArcContainer.class));
        ResultHandle instanceHandle = mc.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(ArcContainer.class, "instance", InstanceHandle.class, Class.class,
                        Annotation[].class),
                containerHandle, mc.loadClass(className),
                mc.newArray(Annotation.class, 0));
        return mc.invokeInterfaceMethod(MethodDescriptor.ofMethod(InstanceHandle.class, "get", Object.class), instanceHandle);
    }

    private String createMethodId(MethodInfo methodInfo) {
        return methodInfo.name() + '('
                + Arrays.toString(methodInfo.parameters().stream().map(mp -> mp.type().name().toString()).toArray()) + ')';
    }

    private void addIfacesWithMessageAnns(IndexView index, List<DotName> annotations, Set<String> detectedForCreate) {
        for (DotName annotation : annotations) {
            Collection<AnnotationInstance> instances = index.getAnnotations(annotation);
            for (AnnotationInstance instance : instances) {
                AnnotationTarget target = instance.target();
                AnnotationTarget.Kind kind = target.kind();
                if (kind == AnnotationTarget.Kind.METHOD) {
                    ClassInfo declaringClass = target.asMethod().declaringClass();
                    if (declaringClass.isInterface()) {
                        detectedForCreate.add(declaringClass.name().toString());
                    }
                } else if (kind == AnnotationTarget.Kind.CLASS) {
                    ClassInfo declaringClass = target.asClass();
                    if (declaringClass.isInterface()) {
                        detectedForCreate.add(declaringClass.name().toString());
                    }
                } else if (kind == AnnotationTarget.Kind.METHOD_PARAMETER) {
                    ClassInfo declaringClass = target.asMethodParameter().method().declaringClass();
                    if (declaringClass.isInterface()) {
                        detectedForCreate.add(declaringClass.name().toString());
                    }
                }

            }
        }
    }

    private static void addCreatedAware(IndexView index, Set<String> detectedForCreate) {
        Collection<AnnotationInstance> instances = index.getAnnotations(LangChain4jDotNames.CREATED_AWARE);
        for (var instance : instances) {
            if (instance.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            detectedForCreate.add(instance.target().asClass().name().toString());
        }
    }

    private AiServiceMethodCreateInfo gatherMethodMetadata(
            MethodInfo method, IndexView index, boolean addMicrometerMetrics,
            boolean addOpenTelemetrySpans, boolean generateResponseSchema,
            Collection<Predicate<AnnotationInstance>> allowedPredicates,
            Collection<Predicate<AnnotationInstance>> ignoredPredicates,
            List<ToolMethodBuildItem> tools,
            List<ToolQualifierProvider.BuildItem> toolQualifierProviders,
            Predicate<MethodInfo> skipOutputFormatInstructionsPredicate,
            Predicate<MethodInfo> fallbackToDummyUserMessagePredicate) {
        validateReturnType(method);

        boolean requiresModeration = method.hasAnnotation(LangChain4jDotNames.MODERATE);
        java.lang.reflect.Type returnType = javaLangReturnType(method);

        List<MethodParameterInfo> params = method.parameters();

        // TODO give user ability to provide custom OutputParser
        String outputFormatInstructions = "";
        if (!skipOutputFormatInstructionsPredicate.test(method)) {
            Optional<JsonSchema> structuredOutputSchema = Optional.empty();
            if (!returnType.equals(Multi.class)) {
                outputFormatInstructions = SERVICE_OUTPUT_PARSER.outputFormatInstructions(returnType);
            }
        }

        Optional<Integer> chatRequestParametersParamPosition = gatherChatRequestParametersParamPosition(method);

        List<TemplateParameterInfo> templateParams = gatherTemplateParamInfo(method, allowedPredicates, ignoredPredicates,
                chatRequestParametersParamPosition);
        Optional<AiServiceMethodCreateInfo.TemplateInfo> systemMessageInfo = gatherSystemMessageInfo(method, templateParams);
        AiServiceMethodCreateInfo.UserMessageInfo userMessageInfo = gatherUserMessageInfo(method, templateParams,
                systemMessageInfo, fallbackToDummyUserMessagePredicate, chatRequestParametersParamPosition);

        AiServiceMethodCreateInfo.ResponseSchemaInfo responseSchemaInfo = ResponseSchemaInfo.of(generateResponseSchema,
                systemMessageInfo,
                userMessageInfo.template(), outputFormatInstructions, jsonSchemaFrom(returnType));

        if (!generateResponseSchema && responseSchemaInfo.isInSystemMessage())
            throw new RuntimeException(
                    "The %s placeholder cannot be used if the property quarkus.langchain4j.response-schema is set to false. Found in: %s"
                            .formatted(ResponseSchemaUtil.placeholder(), method.declaringClass()));

        if (!generateResponseSchema && responseSchemaInfo.isInUserMessage().isPresent()
                && responseSchemaInfo.isInUserMessage().get())
            throw new RuntimeException(
                    "The %s placeholder cannot be used if the property quarkus.langchain4j.response-schema is set to false. Found in: %s"
                            .formatted(ResponseSchemaUtil.placeholder(), method.declaringClass()));

        Optional<Integer> memoryIdParamPosition = gatherMemoryIdParamPosition(method);
        Optional<Integer> overrideChatModelParamPosition = gatherOverrideChatModelParameterPosition(method);
        Optional<AiServiceMethodCreateInfo.MetricsTimedInfo> metricsTimedInfo = gatherMetricsTimedInfo(method,
                addMicrometerMetrics);
        Optional<AiServiceMethodCreateInfo.MetricsCountedInfo> metricsCountedInfo = gatherMetricsCountedInfo(method,
                addMicrometerMetrics);
        Optional<AiServiceMethodCreateInfo.SpanInfo> spanInfo = gatherSpanInfo(method, addOpenTelemetrySpans);
        Map<String, AnnotationLiteral<?>> methodToolClassInfo = gatherMethodToolInfo(method, index,
                toolQualifierProviders.stream().map(
                        ToolQualifierProvider.BuildItem::getProvider).toList());

        List<String> methodMcpClientNames = gatherMethodMcpClientNames(method);
        String accumulatorClassName = AiServicesMethodBuildItem.gatherAccumulator(method);
        String responseAugmenterClassName = AiServicesMethodBuildItem.gatherResponseAugmenter(method);

        //  Detect if tools execution may block the caller thread.
        boolean switchToWorkerThreadForToolExecution = detectIfToolExecutionRequiresAWorkerThread(method, tools,
                methodToolClassInfo.keySet(), methodMcpClientNames, index);

        TypeArgMapper typeArgMapper = new TypeArgMapper(method.declaringClass(), index);
        var methodReturnTypeSignature = typeSignature(method.returnType(), typeArgMapper);

        List<AiServiceMethodCreateInfo.ParameterInfo> parameterInfoList = new ArrayList<>();
        for (MethodParameterInfo p : method.parameters()) {
            parameterInfoList.add(new AiServiceMethodCreateInfo.ParameterInfo(p.name(),
                    typeSignature(p.type(), typeArgMapper),
                    p.declaredAnnotations().stream().map(an -> an.name().toString()).collect(
                            Collectors.toSet())));
        }

        return new AiServiceMethodCreateInfo(method.declaringClass().name().toString(), method.name(), parameterInfoList,
                systemMessageInfo,
                userMessageInfo, memoryIdParamPosition, requiresModeration, methodReturnTypeSignature,
                overrideChatModelParamPosition, chatRequestParametersParamPosition,
                metricsTimedInfo, metricsCountedInfo, spanInfo, responseSchemaInfo,
                methodToolClassInfo, methodMcpClientNames, switchToWorkerThreadForToolExecution,
                accumulatorClassName, responseAugmenterClassName, gatherInputGuardrails(method),
                gatherOutputGuardrails(method, methodReturnTypeSignature));
    }

    private static InputGuardrailsLiteral gatherInputGuardrails(MethodInfo method) {
        return new InputGuardrailsLiteral(
                gatherGuardrails(getGuardrailsAnnotation(method, LangChain4jDotNames.INPUT_GUARDRAILS)));
    }

    private static OutputGuardrailsLiteral gatherOutputGuardrails(MethodInfo method, String methodReturnTypeSignature) {
        var annotationInstance = getGuardrailsAnnotation(method, LangChain4jDotNames.OUTPUT_GUARDRAILS);
        var methodReturnsMulti = TypeUtil.isMulti(TypeSignatureParser.parse(methodReturnTypeSignature));
        var maxRetriesAsSetByConfig = annotationInstance
                .map(v -> v.value("maxRetries"))
                .map(AnnotationValue::asInt)
                .or(() -> ConfigProvider.getConfig().getOptionalValue("quarkus.langchain4j.guardrails.max-retries",
                        Integer.class))
                .orElse(GuardrailsConfig.MAX_RETRIES_DEFAULT);

        // If the method returns a Multi, then we don't want the guardrail service to perform any retries on its own
        // Instead we'll store the value as a config value and we'll have the multi itself perform the retries
        // based on the number of retries configured, either on the annotation or set through config
        return new OutputGuardrailsLiteral(
                gatherGuardrails(annotationInstance),
                methodReturnsMulti ? 0 : maxRetriesAsSetByConfig,
                maxRetriesAsSetByConfig);
    }

    private static Optional<AnnotationInstance> getGuardrailsAnnotation(MethodInfo methodInfo, DotName annotation) {
        return Optional.ofNullable(methodInfo.annotation(annotation))
                .or(() -> getGuardrailsAnnotation(methodInfo.declaringClass(), annotation));
    }

    private static Optional<AnnotationInstance> getGuardrailsAnnotation(ClassInfo classInfo, DotName annotation) {
        return Optional.ofNullable(classInfo.declaredAnnotation(annotation));
    }

    private static Stream<Type> gatherGuardrailsStream(Optional<AnnotationInstance> annotation) {
        return annotation
                .map(AnnotationInstance::value)
                .map(AnnotationValue::asClassArray)
                .map(Arrays::stream)
                .orElseGet(Stream::of)
                .distinct();
    }

    private static List<String> gatherGuardrails(Optional<AnnotationInstance> annotation) {
        return gatherGuardrailsStream(annotation)
                .map(t -> t.name().toString())
                .toList();
    }

    private Optional<JsonSchema> jsonSchemaFrom(java.lang.reflect.Type returnType) {
        if (isMulti(returnType)) {
            return Optional.empty();
        }
        return JsonSchemas.jsonSchemaFrom(returnType);
    }

    private boolean detectIfToolExecutionRequiresAWorkerThread(MethodInfo method, List<ToolMethodBuildItem> tools,
            Collection<String> methodToolClassNames, List<String> mcpClientNames, IndexView index) {
        List<String> allTools = new ArrayList<>(methodToolClassNames);
        DotName toolProviderClassDotName = null;
        // We need to combine it with the tools that are registered globally - unfortunately, we don't have access to the AI service here, so, re-parsing.
        AnnotationInstance annotation = method.declaringClass().annotation(REGISTER_AI_SERVICES);
        if (annotation != null) {
            AnnotationValue value = annotation.value("tools");
            if (value != null) {
                allTools.addAll(Arrays.stream(value.asClassArray()).map(t -> t.name().toString()).toList());
            }
            // Extract toolProvider from annotation
            AnnotationValue toolProviderValue = annotation.value("toolProvider");
            if (toolProviderValue != null) {
                toolProviderClassDotName = toolProviderValue.asClass().name();
            }
        }
        return detectAiServiceMethodThanNeedToBeDispatchedOnWorkerThread(method, allTools, tools, toolProviderClassDotName,
                mcpClientNames, index);
    }

    private void validateReturnType(MethodInfo method) {
        Type returnType = method.returnType();
        Type.Kind returnTypeKind = returnType.kind();
        if (returnTypeKind == Type.Kind.VOID) {
            throw illegalConfiguration("Return type of method '%s' cannot be void", method);
        }
        if ((returnTypeKind != Type.Kind.CLASS) && (returnTypeKind != Type.Kind.PRIMITIVE)
                && (returnTypeKind != Type.Kind.PARAMETERIZED_TYPE)) {
            throw illegalConfiguration("Unsupported type of method '%s", method);
        }

    }

    private java.lang.reflect.Type javaLangReturnType(MethodInfo method) {
        try {
            Class<?> declaringClass = Class.forName(method.declaringClass().name().toString(), false,
                    Thread.currentThread().getContextClassLoader());
            List<Class<?>> methodParamTypes = new ArrayList<>(method.parametersCount());
            for (Type methodParamType : method.parameterTypes()) {
                methodParamTypes.add(JandexUtil.load(methodParamType, Thread.currentThread().getContextClassLoader()));
            }
            return declaringClass.getDeclaredMethod(method.name(), methodParamTypes.toArray(EMPTY_CLASS_ARRAY))
                    .getGenericReturnType();
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private String typeSignature(Type returnType, TypeArgMapper typeArgMapper) {
        return AsmUtil.getSignature(returnType, typeArgMapper);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private List<TemplateParameterInfo> gatherTemplateParamInfo(MethodInfo method,
            Collection<Predicate<AnnotationInstance>> allowedPredicates,
            Collection<Predicate<AnnotationInstance>> ignoredPredicates,
            Optional<Integer> chatRequestParametersParamPosition) {
        if (method.parameters().isEmpty()) {
            return Collections.emptyList();
        }

        List<TemplateParameterInfo> templateParams = new ArrayList<>();
        for (MethodParameterInfo param : method.parameters()) {
            if (chatRequestParametersParamPosition.isPresent()
                    && param.position() == chatRequestParametersParamPosition.get()) {
                continue;
            }

            if (isParameterAllowedAsTemplateVariable(param, allowedPredicates, ignoredPredicates)) {
                templateParams.add(new TemplateParameterInfo(param.position(), param.name()));
            } else {
                AnnotationInstance vInstance = param.annotation(V);
                if (vInstance != null) {
                    AnnotationValue value = vInstance.value();
                    if (value != null) {
                        templateParams.add(new TemplateParameterInfo(param.position(), value.asString()));
                    }
                }
            }
        }

        if (!templateParams.isEmpty() && templateParams.stream().map(TemplateParameterInfo::name).allMatch(Objects::isNull)) {
            if (!method.declaringClass().name().toString().startsWith("dev.langchain4j")) { // ignore langchain4j support classes
                log.warn(
                        "The application has been compiled without the '-parameters' being set flag on javac. Make sure your build tool is configured to pass this flag to javac, otherwise Quarkus LangChain4j is unlikely to work properly without it.");
            }

        }

        if ((templateParams.size() == 1) && (method.parameters().size() == 1)) {
            // the special 'it' param is supported when the method only has one parameter
            templateParams.add(new TemplateParameterInfo(0, "it"));
        }

        return templateParams;
    }

    private boolean isParameterAllowedAsTemplateVariable(
            MethodParameterInfo param, Collection<Predicate<AnnotationInstance>> allowedPredicates,
            Collection<Predicate<AnnotationInstance>> ignoredPredicates) {
        if (param.type().name().equals(INVOCATION_PARAMETERS)) {
            return false;
        }

        Collection<MethodParameterAsTemplateVariableAllowance> allowances = param.annotations().stream().map(anno -> {

            String annotationName = anno.name().toString();

            if (allowedPredicates.stream().anyMatch(predicate -> predicate.test(anno))) {
                // Any annotation matching one of the allowed predicates forcedly enable param as template variable
                log.debugf("Annotation %s matches an allowed predicate, parameter could be used as template variable.",
                        annotationName);
                return FORCE_ALLOW;
            } else if (ignoredPredicates.stream().anyMatch(predicate -> predicate.test(anno))) {
                // Any annotation matching one of the ignored predicates is ignored
                log.debugf(
                        "Annotation %s matches an ignored predicate, remaining annotations decide parameter allowance as template variable.",
                        annotationName);
                return IGNORE;
            }

            // Remaining annotations are denied, unless co-located to an allowed annotation
            log.debugf(
                    "Annotation %s doesn't match any predicate, parameter could not be used as template variable unless force allowed by another annotation.",
                    annotationName);
            return OPTIONAL_DENY;
        }).collect(Collectors.toSet());

        return allowances.contains(FORCE_ALLOW) || !allowances.contains(OPTIONAL_DENY);
    }

    private Optional<AiServiceMethodCreateInfo.TemplateInfo> gatherSystemMessageInfo(MethodInfo method,
            List<TemplateParameterInfo> templateParams) {
        AnnotationInstance instance = method.annotation(LangChain4jDotNames.SYSTEM_MESSAGE);
        if (instance == null) { // try and see if the class is annotated with @SystemMessage
            instance = method.declaringClass().declaredAnnotation(LangChain4jDotNames.SYSTEM_MESSAGE);
        }
        if (instance != null) {
            String systemMessageTemplate = TemplateUtil.getTemplateFromAnnotationInstance(instance);
            if (systemMessageTemplate.isEmpty()) {
                throw illegalConfigurationForMethod("@SystemMessage's template parameter cannot be empty", method);
            }

            // TODO: we should probably add a lot more template validation here
            return Optional.of(
                    AiServiceMethodCreateInfo.TemplateInfo.fromText(
                            systemMessageTemplate,
                            TemplateParameterInfo.toNameToArgsPositionMap(templateParams)));
        }
        return Optional.empty();
    }

    private Optional<Integer> gatherMemoryIdParamPosition(MethodInfo method) {
        return method.annotations(LangChain4jDotNames.MEMORY_ID).stream().filter(IS_METHOD_PARAMETER_ANNOTATION)
                .map(METHOD_PARAMETER_POSITION_FUNCTION)
                .findFirst();
    }

    private Optional<Integer> gatherOverrideChatModelParameterPosition(MethodInfo method) {
        var result = method.annotations(LangChain4jDotNames.MODEL_NAME).stream().filter(IS_METHOD_PARAMETER_ANNOTATION)
                .map(METHOD_PARAMETER_POSITION_FUNCTION)
                .findFirst();
        if (result.isPresent()) {
            if (!DotNames.STRING.equals(method.parameterTypes().get(result.get()).name())) {
                throw illegalConfigurationForMethod("Method parameters annotated with @ModelName can only be of type 'String'",
                        method);
            }
        }
        return result;
    }

    private Optional<Integer> gatherChatRequestParametersParamPosition(MethodInfo method) {
        Optional<Integer> result = Optional.empty();
        for (int i = 0; i < method.parametersCount(); i++) {
            if (method.parameterType(i).name().equals(CHAT_REQUEST_PARAMETERS)) {
                if (result.isPresent()) {
                    throw illegalConfigurationForMethod(
                            "Only one parameter of type ChatRequestParameters is allowed", method);
                }
                result = Optional.of(i);
            }
        }
        return result;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private AiServiceMethodCreateInfo.UserMessageInfo gatherUserMessageInfo(MethodInfo method,
            List<TemplateParameterInfo> templateParams,
            Optional<AiServiceMethodCreateInfo.TemplateInfo> systemMessageInfo,
            Predicate<MethodInfo> fallbackToDummyUserMesage,
            Optional<Integer> chatRequestParametersParamPosition) {

        Optional<Integer> userNameParamPosition = method.annotations(LangChain4jDotNames.USER_NAME).stream().filter(
                IS_METHOD_PARAMETER_ANNOTATION).map(METHOD_PARAMETER_POSITION_FUNCTION).findFirst();
        Optional<Integer> imageParamPosition = determineImageParamPosition(method);
        if (imageParamPosition.isPresent()) {
            MethodParameterInfo imageUrlParam = method.parameters().get(imageParamPosition.get());
            validateImageUrlParam(imageUrlParam);
        }
        Optional<Integer> audioParamPosition = determineAudioParamPosition(method);
        if (audioParamPosition.isPresent()) {
            MethodParameterInfo audioUrlParam = method.parameters().get(audioParamPosition.get());
            validateAudioUrlParam(audioUrlParam);
        }
        Optional<Integer> pdfParamPosition = determinePdfParamPosition(method);
        if (pdfParamPosition.isPresent()) {
            MethodParameterInfo pdfUrlParam = method.parameters().get(pdfParamPosition.get());
            validatePdfUrlParam(pdfUrlParam);
        }
        Optional<Integer> videoParamPosition = determineVideoParamPosition(method);
        if (videoParamPosition.isPresent()) {
            MethodParameterInfo videoUrlParam = method.parameters().get(videoParamPosition.get());
            validateVideoUrlParam(videoUrlParam);
        }

        AnnotationInstance userMessageInstance = method.declaredAnnotation(LangChain4jDotNames.USER_MESSAGE);
        if (userMessageInstance != null) {
            String userMessageTemplate = TemplateUtil.getTemplateFromAnnotationInstance(userMessageInstance);

            if (userMessageTemplate.contains("{{it}}")) {
                if (method.parametersCount() != 1) {
                    throw illegalConfigurationForMethod(
                            "Error: The {{it}} placeholder is present but the method does not have exactly one parameter. " +
                                    "Please ensure that methods using the {{it}} placeholder have exactly one parameter",
                            method);
                }
            }

            // TODO: we should probably add a lot more template validation here
            return AiServiceMethodCreateInfo.UserMessageInfo.fromTemplate(
                    AiServiceMethodCreateInfo.TemplateInfo.fromText(userMessageTemplate,
                            TemplateParameterInfo.toNameToArgsPositionMap(templateParams)),
                    userNameParamPosition);
        } else {
            Optional<AnnotationInstance> userMessageOnMethodParam = method.annotations(LangChain4jDotNames.USER_MESSAGE)
                    .stream()
                    .filter(IS_METHOD_PARAMETER_ANNOTATION).findFirst();
            if (userMessageOnMethodParam.isPresent()) {
                if (DotNames.STRING.equals(userMessageOnMethodParam.get().target().asMethodParameter().type().name())
                        && !templateParams.isEmpty()) {
                    return AiServiceMethodCreateInfo.UserMessageInfo.fromTemplate(
                            AiServiceMethodCreateInfo.TemplateInfo.fromMethodParam(
                                    Short.valueOf(userMessageOnMethodParam.get().target().asMethodParameter().position())
                                            .intValue(),
                                    TemplateParameterInfo.toNameToArgsPositionMap(templateParams)),
                            userNameParamPosition);
                } else {
                    return AiServiceMethodCreateInfo.UserMessageInfo.fromMethodParam(
                            userMessageOnMethodParam.get().target().asMethodParameter().position(),
                            userNameParamPosition);
                }
            } else {
                Set<String> templateParamNames = Collections.EMPTY_SET;
                if (systemMessageInfo.isPresent() && systemMessageInfo.get().text().isPresent()) {
                    templateParamNames = TemplateUtil.parts(systemMessageInfo.get().text().get()).stream()
                            .flatMap(l -> l.stream().map(
                                    Expression.Part::getName))
                            .collect(Collectors.toSet());
                }
                int userMessageParamPosition = -1;
                int undefinedParams = 0;
                for (int i = 0; i < method.parametersCount(); i++) {
                    MethodParameterInfo parameter = method.parameters().get(i);
                    if (templateParamNames.contains(parameter.name())) {
                        continue;
                    } else if (userNameParamPosition.isPresent() && i == userNameParamPosition.get()) {
                        continue;
                    } else if (imageParamPosition.isPresent() && i == imageParamPosition.get()) {
                        continue;
                    } else if (audioParamPosition.isPresent() && i == audioParamPosition.get()) {
                        continue;
                    } else if (pdfParamPosition.isPresent() && i == pdfParamPosition.get()) {
                        continue;
                    } else if (videoParamPosition.isPresent() && i == videoParamPosition.get()) {
                        continue;
                    } else if (parameter.type().name().equals(INVOCATION_PARAMETERS)) {
                        continue;
                    } else if (chatRequestParametersParamPosition.isPresent()
                            && i == chatRequestParametersParamPosition.get()) {
                        continue;
                    } else if (parameter.hasAnnotation(LangChain4jDotNames.MEMORY_ID)) {
                        continue;
                    }
                    undefinedParams++;
                    if (undefinedParams > 1) {
                        if (fallbackToDummyUserMesage.test(method)) {
                            return AiServiceMethodCreateInfo.UserMessageInfo.fromTemplate(
                                    AiServiceMethodCreateInfo.TemplateInfo.fromText("", Map.of()), Optional.empty());
                        }

                        throw illegalConfigurationForMethod(
                                "For methods with multiple parameters, each parameter must be annotated with @V (or match an template parameter by name), @UserMessage, @UserName or @MemoryId",
                                method);
                    }
                    userMessageParamPosition = i;
                }
                if (userMessageParamPosition == -1) {
                    // There is no user message
                    return new AiServiceMethodCreateInfo.UserMessageInfo(Optional.empty(), Optional.empty(), Optional.empty());
                } else {
                    return AiServiceMethodCreateInfo.UserMessageInfo.fromMethodParam(userMessageParamPosition,
                            userNameParamPosition);

                }
            }
        }
    }

    private static Optional<Integer> determineImageParamPosition(MethodInfo method) {
        Optional<Integer> result = method.annotations(LangChain4jDotNames.IMAGE_URL).stream().filter(
                IS_METHOD_PARAMETER_ANNOTATION).map(METHOD_PARAMETER_POSITION_FUNCTION).findFirst();
        if (result.isPresent()) {
            return result;
        }
        // we don't need @ImageUrl if the parameter is of type Image or List<Image>
        return method.parameters().stream()
                .filter(pi -> pi.type().name().equals(LangChain4jDotNames.IMAGE)
                        || isListOf(pi.type(), LangChain4jDotNames.IMAGE))
                .map(pi -> (int) pi.position()).findFirst();
    }

    private static Optional<Integer> determineAudioParamPosition(MethodInfo method) {
        Optional<Integer> result = method.annotations(LangChain4jDotNames.AUDIO_URL).stream().filter(
                IS_METHOD_PARAMETER_ANNOTATION).map(METHOD_PARAMETER_POSITION_FUNCTION).findFirst();
        if (result.isPresent()) {
            return result;
        }
        // we don't need @AudioUrl if the parameter is of type Audio or List<Audio>
        return method.parameters().stream()
                .filter(pi -> pi.type().name().equals(LangChain4jDotNames.AUDIO)
                        || isListOf(pi.type(), LangChain4jDotNames.AUDIO))
                .map(pi -> (int) pi.position()).findFirst();
    }

    private static Optional<Integer> determinePdfParamPosition(MethodInfo method) {
        Optional<Integer> result = method.annotations(LangChain4jDotNames.PDF_URL).stream().filter(
                IS_METHOD_PARAMETER_ANNOTATION).map(METHOD_PARAMETER_POSITION_FUNCTION).findFirst();
        if (result.isPresent()) {
            return result;
        }
        // we don't need @PdfUrl if the parameter is of type PdfFile or List<PdfFile>
        return method.parameters().stream()
                .filter(pi -> pi.type().name().equals(LangChain4jDotNames.PDF_FILE)
                        || isListOf(pi.type(), LangChain4jDotNames.PDF_FILE))
                .map(pi -> (int) pi.position()).findFirst();
    }

    private static Optional<Integer> determineVideoParamPosition(MethodInfo method) {
        Optional<Integer> result = method.annotations(LangChain4jDotNames.VIDEO_URL).stream().filter(
                IS_METHOD_PARAMETER_ANNOTATION).map(METHOD_PARAMETER_POSITION_FUNCTION).findFirst();
        if (result.isPresent()) {
            return result;
        }
        // we don't need @VideoUrl if the parameter is of type Video or List<Video>
        return method.parameters().stream()
                .filter(pi -> pi.type().name().equals(LangChain4jDotNames.VIDEO)
                        || isListOf(pi.type(), LangChain4jDotNames.VIDEO))
                .map(pi -> (int) pi.position()).findFirst();
    }

    private static boolean isListOf(org.jboss.jandex.Type type, DotName elementType) {
        if (type.kind() == org.jboss.jandex.Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType pt = type.asParameterizedType();
            return DotNames.LIST.equals(pt.name())
                    && pt.arguments().size() == 1
                    && pt.arguments().get(0).name().equals(elementType);
        }
        return false;
    }

    private void validateImageUrlParam(MethodParameterInfo param) {
        if (param == null) {
            throw new IllegalArgumentException("Unhandled @ImageUrl annotation");
        }
        Type type = param.type();
        DotName typeName = type.name();
        if (typeName.equals(DotNames.STRING) || typeName.equals(DotNames.URI) || typeName.equals(DotNames.URL)
                || typeName.equals(LangChain4jDotNames.IMAGE)
                || isListOf(type, LangChain4jDotNames.IMAGE)) {
            return;
        }
        throw new IllegalArgumentException("Unhandled @ImageUrl type '" + type.name() + "'");
    }

    private void validateAudioUrlParam(MethodParameterInfo param) {
        if (param == null) {
            throw new IllegalArgumentException("Unhandled @AudioUrl annotation");
        }
        Type type = param.type();
        DotName typeName = type.name();
        if (typeName.equals(DotNames.STRING) || typeName.equals(DotNames.URI) || typeName.equals(DotNames.URL)
                || typeName.equals(LangChain4jDotNames.AUDIO)
                || isListOf(type, LangChain4jDotNames.AUDIO)) {
            return;
        }
        throw new IllegalArgumentException("Unhandled @AudioUrl type '" + type.name() + "'");
    }

    private void validatePdfUrlParam(MethodParameterInfo param) {
        if (param == null) {
            throw new IllegalArgumentException("Unhandled @PdfUrl annotation");
        }
        Type type = param.type();
        DotName typeName = type.name();
        if (typeName.equals(DotNames.STRING) || typeName.equals(DotNames.URI) || typeName.equals(DotNames.URL)
                || typeName.equals(LangChain4jDotNames.PDF_FILE)
                || isListOf(type, LangChain4jDotNames.PDF_FILE)) {
            return;
        }
        throw new IllegalArgumentException("Unhandled @PdfUrl type '" + type.name() + "'");
    }

    private void validateVideoUrlParam(MethodParameterInfo param) {
        if (param == null) {
            throw new IllegalArgumentException("Unhandled @VideoUrl annotation");
        }
        Type type = param.type();
        DotName typeName = type.name();
        if (typeName.equals(DotNames.STRING) || typeName.equals(DotNames.URI) || typeName.equals(DotNames.URL)
                || typeName.equals(LangChain4jDotNames.VIDEO)
                || isListOf(type, LangChain4jDotNames.VIDEO)) {
            return;
        }
        throw new IllegalArgumentException("Unhandled @VideoUrl type '" + type.name() + "'");
    }

    private Optional<AiServiceMethodCreateInfo.MetricsTimedInfo> gatherMetricsTimedInfo(MethodInfo method,
            boolean addMicrometerMetrics) {
        if (!addMicrometerMetrics) {
            return Optional.empty();
        }

        String name = METRICS_DEFAULT_NAME + ".timed";
        List<String> tags = defaultMetricsTags(method);

        AnnotationInstance timedInstance = method.annotation(MICROMETER_TIMED);
        if (timedInstance == null) {
            timedInstance = method.declaringClass().declaredAnnotation(MICROMETER_TIMED);
        }

        if (timedInstance == null) {
            // we default to having all AiServices being timed
            return Optional.of(new AiServiceMethodCreateInfo.MetricsTimedInfo.Builder(name)
                    .setExtraTags(tags.toArray(EMPTY_STRING_ARRAY)).build());
        }

        AnnotationValue nameValue = timedInstance.value();
        if (nameValue != null) {
            String nameStr = nameValue.asString();
            if (nameStr != null && !nameStr.isEmpty()) {
                name = nameStr;
            }
        }

        var builder = new AiServiceMethodCreateInfo.MetricsTimedInfo.Builder(name);

        AnnotationValue extraTagsValue = timedInstance.value("extraTags");
        if (extraTagsValue != null) {
            tags.addAll(Arrays.asList(extraTagsValue.asStringArray()));
        }
        builder.setExtraTags(tags.toArray(EMPTY_STRING_ARRAY));

        AnnotationValue longTaskValue = timedInstance.value("longTask");
        if (longTaskValue != null) {
            builder.setLongTask(longTaskValue.asBoolean());
        }

        AnnotationValue percentilesValue = timedInstance.value("percentiles");
        if (percentilesValue != null) {
            builder.setPercentiles(percentilesValue.asDoubleArray());
        }

        AnnotationValue histogramValue = timedInstance.value("histogram");
        if (histogramValue != null) {
            builder.setHistogram(histogramValue.asBoolean());
        }

        AnnotationValue descriptionValue = timedInstance.value("description");
        if (descriptionValue != null) {
            builder.setDescription(descriptionValue.asString());
        }

        return Optional.of(builder.build());
    }

    private Optional<AiServiceMethodCreateInfo.MetricsCountedInfo> gatherMetricsCountedInfo(MethodInfo method,
            boolean addMicrometerMetrics) {
        if (!addMicrometerMetrics) {
            return Optional.empty();
        }

        String name = METRICS_DEFAULT_NAME + ".counted";
        List<String> tags = defaultMetricsTags(method);

        AnnotationInstance timedInstance = method.annotation(MICROMETER_COUNTED);
        if (timedInstance == null) {
            timedInstance = method.declaringClass().declaredAnnotation(MICROMETER_COUNTED);
        }

        if (timedInstance == null) {
            // we default to having all AiServices being timed
            return Optional.of(new AiServiceMethodCreateInfo.MetricsCountedInfo.Builder(name)
                    .setExtraTags(tags.toArray(EMPTY_STRING_ARRAY)).build());
        }

        AnnotationValue nameValue = timedInstance.value();
        if (nameValue != null) {
            String nameStr = nameValue.asString();
            if (nameStr != null && !nameStr.isEmpty()) {
                name = nameStr;
            }
        }

        var builder = new AiServiceMethodCreateInfo.MetricsCountedInfo.Builder(name);

        AnnotationValue extraTagsValue = timedInstance.value("extraTags");
        if (extraTagsValue != null) {
            tags.addAll(Arrays.asList(extraTagsValue.asStringArray()));
        }
        builder.setExtraTags(tags.toArray(EMPTY_STRING_ARRAY));

        AnnotationValue recordFailuresOnlyValue = timedInstance.value("recordFailuresOnly");
        if (recordFailuresOnlyValue != null) {
            builder.setRecordFailuresOnly(recordFailuresOnlyValue.asBoolean());
        }

        AnnotationValue descriptionValue = timedInstance.value("description");
        if (descriptionValue != null) {
            builder.setDescription(descriptionValue.asString());
        }

        return Optional.of(builder.build());
    }

    private List<String> defaultMetricsTags(MethodInfo method) {
        List<String> tags = new ArrayList<>(4);
        tags.add("aiservice");
        tags.add(method.declaringClass().name().withoutPackagePrefix());
        tags.add("method");
        tags.add(method.name());
        return tags;
    }

    private Optional<AiServiceMethodCreateInfo.SpanInfo> gatherSpanInfo(MethodInfo method,
            boolean addOpenTelemetrySpans) {
        if (!addOpenTelemetrySpans) {
            return Optional.empty();
        }

        String name = defaultAiServiceSpanName(method);

        // TODO: add more

        return Optional.of(new AiServiceMethodCreateInfo.SpanInfo(name));
    }

    private Map<String, AnnotationLiteral<?>> gatherMethodToolInfo(MethodInfo method, IndexView index,
            List<ToolQualifierProvider> toolQualifierProviders) {
        Map<String, AnnotationLiteral<?>> result = new HashMap<>();
        for (String methodToolClassName : gatherMethodToolClassNames(method)) {
            ClassInfo classInfo = index.getClassByName(methodToolClassName);
            if (classInfo == null) {
                result.put(methodToolClassName, null);
            } else {
                AnnotationLiteral<?> qualifier = null;
                for (ToolQualifierProvider toolQualifierProvider : toolQualifierProviders) {
                    if (toolQualifierProvider.supports(classInfo)) {
                        qualifier = toolQualifierProvider.qualifier(classInfo);
                    }
                }
                result.put(methodToolClassName, qualifier);
            }
        }
        return result;
    }

    private List<String> gatherMethodToolClassNames(MethodInfo method) {
        AnnotationInstance toolBoxInstance = method.declaredAnnotation(ToolBox.class);
        if (toolBoxInstance == null) {
            return Collections.emptyList();
        }

        AnnotationValue toolBoxValue = toolBoxInstance.value();
        if (toolBoxValue == null) {
            return Collections.emptyList();
        }

        Type[] toolClasses = toolBoxValue.asClassArray();
        if (toolClasses.length == 0) {
            return Collections.emptyList();
        }

        return Arrays.stream(toolClasses).map(t -> t.name().toString()).collect(Collectors.toList());
    }

    private List<String> gatherMethodMcpClientNames(MethodInfo method) {
        AnnotationInstance mcpToolBoxInstance = method.declaredAnnotation(LangChain4jDotNames.MCP_TOOLBOX);
        if (mcpToolBoxInstance == null) {
            return null;
        }

        AnnotationValue mcpToolBoxValue = mcpToolBoxInstance.value();
        if (mcpToolBoxValue == null) {
            return Collections.emptyList();
        }

        String[] mcpClientNames = mcpToolBoxValue.asStringArray();
        if (mcpClientNames.length == 0) {
            return Collections.emptyList();
        }

        return Arrays.asList(mcpClientNames);
    }

    private DotName determineThinkingHandler(ClassInfo iface, ClassOutput classOutput) {
        List<AnnotationInstance> annotations = iface.annotations(LangChain4jDotNames.ON_THINKING);
        if (annotations.isEmpty()) {
            return null;
        }
        if (annotations.size() > 1) {
            throw new IllegalConfigurationException(
                    "Only a single @OnThinking annotation is allowed per AiService. Offending class is '" + iface.name() + "'");
        }
        AnnotationTarget target = annotations.get(0).target();
        if (target.kind() != AnnotationTarget.Kind.METHOD) {
            throw new IllegalConfigurationException(
                    "The @OnThinking annotation can only be placed on methods. Offending target is '" + target + "'");
        }
        MethodInfo method = target.asMethod();
        String location = method.declaringClass().name() + "#" + method.name();
        if (!Modifier.isStatic(method.flags())) {
            throw new IllegalConfigurationException(
                    "The @OnThinking annotation can only be placed on static methods. Offending method is '" + location + "'");
        }
        if (method.returnType().kind() != Type.Kind.VOID) {
            throw new IllegalConfigurationException(
                    "The @OnThinking annotation can only be placed on methods that return void. Offending method is '"
                            + location + "'");
        }
        if (method.parameterTypes().size() != 1
                || !LangChain4jDotNames.THINKING_EMITTED.equals(method.parameterTypes().get(0).name())) {
            throw new IllegalConfigurationException(
                    "The @OnThinking annotation can only be placed on methods that take a single "
                            + LangChain4jDotNames.THINKING_EMITTED + " parameter. Offending method is '" + location + "'");
        }
        return DotName.createSimple(generateAiServiceThinkingHandler(iface, method, classOutput));
    }

    /**
     * Generates a class that looks like the following:
     *
     * <pre>
     * {@code
     * public class SomeAiService$$QuarkusThinkingHandler implements ThinkingHandler {
     *
     *     &#64;Override
     *     public void emit(ThinkingEmitted event) {
     *         SomeAiService.onThinking(event);
     *     }
     * }
     * }
     * </pre>
     */
    private String generateAiServiceThinkingHandler(ClassInfo iface, MethodInfo thinkingTargetMethod,
            ClassOutput classOutput) {
        String implClassName = iface.name() + "$$QuarkusThinkingHandler";

        ClassCreator.Builder classCreatorBuilder = ClassCreator.builder()
                .classOutput(classOutput)
                .className(implClassName)
                .interfaces(ThinkingHandler.class.getName());
        try (ClassCreator classCreator = classCreatorBuilder.build()) {
            MethodCreator methodCreator = classCreator.getMethodCreator("emit", void.class, ThinkingEmitted.class);
            methodCreator.invokeStaticInterfaceMethod(
                    MethodDescriptor.ofMethod(
                            thinkingTargetMethod.declaringClass().name().toString(),
                            thinkingTargetMethod.name(),
                            void.class,
                            ThinkingEmitted.class),
                    methodCreator.getMethodParam(0));
            methodCreator.returnVoid();
        }

        return implClassName;
    }

    private DotName determineChatMemorySeeder(ClassInfo iface, ClassOutput classOutput) {
        List<AnnotationInstance> annotations = iface.annotations(SEED_MEMORY);
        if (annotations.isEmpty()) {
            return null;
        }
        if (annotations.size() > 1) {
            throw new IllegalConfigurationException(
                    "Only a single @SeedMemory annotation is allowed per AiService. Offending class is '" + iface.name() + "'");
        }
        AnnotationInstance seedMemoryInstance = annotations.get(0);
        AnnotationTarget seedMemoryTarget = seedMemoryInstance.target();
        if (seedMemoryTarget.kind() != AnnotationTarget.Kind.METHOD) {
            throw new IllegalConfigurationException(
                    "The @SeedMemory annotation can only be placed on methods. Offending target is '" + seedMemoryTarget + "'");
        }
        return DotName.createSimple(generateAiServiceChatMemorySeeder(iface, seedMemoryTarget.asMethod(), classOutput));
    }

    /**
     * Generates a class that looks like the following:
     *
     * <pre>
     * {@code
     * public class SomeAiService$$QuarkusChatMemorySeeder implements ChatMemorySeeder {
     *
     *     @Override
     *     public List<ChatMessage> seed(Context context) {
     *         return SomeAiService.someMethod(context.methodName());
     *     }
     * }
     * }
     * </pre>
     */
    private String generateAiServiceChatMemorySeeder(ClassInfo iface, MethodInfo seedMemoryTargetMethod,
            ClassOutput classOutput) {
        if (!Modifier.isStatic(seedMemoryTargetMethod.flags())) {
            throw new IllegalConfigurationException(
                    "The @SeedMemory annotation can only be placed on static methods. Offending method is '"
                            + seedMemoryTargetMethod.declaringClass().name() + "#" + seedMemoryTargetMethod.name() + "'");
        }

        boolean hasListChatMessageReturnType = false;
        if (seedMemoryTargetMethod.returnType().kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType parameterizedType = seedMemoryTargetMethod.returnType().asParameterizedType();
            if (DotNames.LIST.equals(parameterizedType.name()) && (parameterizedType.arguments().size() == 1)) {
                hasListChatMessageReturnType = LangChain4jDotNames.CHAT_MESSAGE
                        .equals(parameterizedType.arguments().get(0).name());
            }
        }
        if (!hasListChatMessageReturnType) {
            throw new IllegalConfigurationException(
                    "The @SeedMemory annotation can only be placed on methods that return List<ChatMessage>. Offending method is '"
                            + seedMemoryTargetMethod.declaringClass().name() + "#" + seedMemoryTargetMethod.name() + "'");
        }

        String implClassName = iface.name() + "$$QuarkusChatMemorySeeder";

        ClassCreator.Builder classCreatorBuilder = ClassCreator.builder()
                .classOutput(classOutput)
                .className(implClassName)
                .interfaces(ChatMemorySeeder.class.getName());
        try (ClassCreator classCreator = classCreatorBuilder.build()) {
            MethodCreator methodCreator = classCreator.getMethodCreator("seed", List.class, ChatMemorySeeder.Context.class);

            LinkedHashMap<String, ResultHandle> seedMemoryTargetMethodParams = new LinkedHashMap<>();
            for (Type paramType : seedMemoryTargetMethod.parameterTypes()) {
                ResultHandle targetMethodParamHandle;
                if (paramType.name().equals(DotNames.STRING)) {
                    targetMethodParamHandle = methodCreator.invokeVirtualMethod(CHAT_MEMORY_SEEDER_CONTEXT_METHOD_NAME,
                            methodCreator.getMethodParam(0));
                } else {
                    throw new IllegalConfigurationException(
                            "The @SeedMemory annotation can only be placed on methods can only take parameters of type 'String' (or no parameters at all). Offending method is '"
                                    + seedMemoryTargetMethod.declaringClass().name() + "#" + seedMemoryTargetMethod.name()
                                    + "'");
                }
                seedMemoryTargetMethodParams.put(paramType.name().toString(), targetMethodParamHandle);
            }

            if (seedMemoryTargetMethodParams.isEmpty()) {
                ResultHandle resultHandle = methodCreator.invokeStaticInterfaceMethod(
                        MethodDescriptor.ofMethod(
                                seedMemoryTargetMethod.declaringClass().name().toString(),
                                seedMemoryTargetMethod.name(),
                                seedMemoryTargetMethod.returnType().name().toString()));
                methodCreator.returnValue(resultHandle);
            } else {
                ResultHandle resultHandle = methodCreator.invokeStaticInterfaceMethod(
                        MethodDescriptor.ofMethod(
                                seedMemoryTargetMethod.declaringClass().name().toString(),
                                seedMemoryTargetMethod.name(),
                                seedMemoryTargetMethod.returnType().name().toString(),
                                seedMemoryTargetMethodParams.keySet().toArray(EMPTY_STRING_ARRAY)),
                        seedMemoryTargetMethodParams.values().toArray(EMPTY_RESULT_HANDLES_ARRAY));
                methodCreator.returnValue(resultHandle);
            }

        }

        return implClassName;
    }

    private String defaultAiServiceSpanName(MethodInfo method) {
        return "langchain4j.aiservices." + method.declaringClass().name().withoutPackagePrefix() + "." + method.name();
    }

    private record TemplateParameterInfo(int position, String name) {

        static Map<String, Integer> toNameToArgsPositionMap(List<TemplateParameterInfo> list) {
            return list.stream()
                    .collect(Collectors.toMap(TemplateParameterInfo::name, TemplateParameterInfo::position));
        }
    }

}
