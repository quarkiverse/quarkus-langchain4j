package io.quarkiverse.langchain4j.deployment;

import static dev.langchain4j.service.IllegalConfigurationException.illegalConfiguration;
import static io.quarkiverse.langchain4j.deployment.ExceptionUtil.illegalConfigurationForMethod;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.BEAN_IF_EXISTS_RETRIEVAL_AUGMENTOR_SUPPLIER;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.INPUT_GUARDRAILS;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.MEMORY_ID;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.NO_RETRIEVAL_AUGMENTOR_SUPPLIER;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.OUTPUT_GUARDRAILS;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.REGISTER_AI_SERVICES;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.SEED_MEMORY;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.V;
import static io.quarkiverse.langchain4j.deployment.MethodParameterAsTemplateVariableAllowance.FORCE_ALLOW;
import static io.quarkiverse.langchain4j.deployment.MethodParameterAsTemplateVariableAllowance.IGNORE;
import static io.quarkiverse.langchain4j.deployment.MethodParameterAsTemplateVariableAllowance.OPTIONAL_DENY;
import static io.quarkiverse.langchain4j.deployment.ObjectSubstitutionUtil.registerJsonSchema;
import static io.quarkiverse.langchain4j.runtime.types.TypeUtil.isMulti;
import static io.quarkus.arc.processor.DotNames.NAMED;

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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.interceptor.InterceptorBinding;

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

import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.Moderate;
import dev.langchain4j.service.output.JsonSchemas;
import dev.langchain4j.service.output.ServiceOutputParser;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkiverse.langchain4j.deployment.config.LangChain4jBuildConfig;
import io.quarkiverse.langchain4j.deployment.devui.ToolProviderInfo;
import io.quarkiverse.langchain4j.deployment.items.AiServicesMethodBuildItem;
import io.quarkiverse.langchain4j.deployment.items.MethodParameterAllowedAnnotationsBuildItem;
import io.quarkiverse.langchain4j.deployment.items.MethodParameterIgnoredAnnotationsBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ToolMethodBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ToolQualifierProvider;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
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
import io.quarkiverse.langchain4j.runtime.aiservice.DeclarativeAiServiceCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.MetricsCountedWrapper;
import io.quarkiverse.langchain4j.runtime.aiservice.MetricsTimedWrapper;
import io.quarkiverse.langchain4j.runtime.aiservice.QuarkusAiServiceContext;
import io.quarkiverse.langchain4j.runtime.aiservice.SpanWrapper;
import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
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

    public static final DotName MICROMETER_TIMED = DotName.createSimple("io.micrometer.core.annotation.Timed");
    public static final DotName MICROMETER_COUNTED = DotName.createSimple("io.micrometer.core.annotation.Counted");
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

    public static final MethodDescriptor CHAT_MEMORY_SEEDER_CONTEXT_METHOD_NAME = MethodDescriptor
            .ofMethod(ChatMemorySeeder.Context.class, "methodName", String.class);

    private static final String METRICS_DEFAULT_NAME = "langchain4j.aiservices";

    private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final ResultHandle[] EMPTY_RESULT_HANDLES_ARRAY = new ResultHandle[0];

    private static final ServiceOutputParser SERVICE_OUTPUT_PARSER = new QuarkusServiceOutputParser(); // TODO: this might need to be improved

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

        // needed because various LLMs use these, so let's be proactive
        // there isn't one great place to put this, so this is probably as good as any
        reflectiveClassProducer.produce(
                ReflectiveClassBuildItem.builder(PropertyNamingStrategies.SnakeCaseStrategy.class).constructors().build());
        reflectiveClassProducer.produce(
                ReflectiveClassBuildItem.builder(PropertyNamingStrategies.LowerCamelCaseStrategy.class).constructors().build());
    }

    @BuildStep
    public void findDeclarativeServices(CombinedIndexBuildItem indexBuildItem,
            CustomScopeAnnotationsBuildItem customScopes,
            BuildProducer<RequestChatModelBeanBuildItem> requestChatModelBeanProducer,
            BuildProducer<RequestModerationModelBeanBuildItem> requestModerationModelBeanProducer,
            BuildProducer<RequestImageModelBeanBuildItem> requestImageModelBeanProducer,
            BuildProducer<DeclarativeAiServiceBuildItem> declarativeAiServiceProducer,
            BuildProducer<ToolProviderMetaBuildItem> toolProviderProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<GeneratedClassBuildItem> generatedClassProducer) {
        IndexView index = indexBuildItem.getIndex();

        Set<String> chatModelNames = new HashSet<>();
        Set<String> moderationModelNames = new HashSet<>();
        Set<String> imageModelNames = new HashSet<>();
        List<ToolProviderInfo> toolProviderInfos = new ArrayList<>();
        ClassOutput generatedClassOutput = new GeneratedClassGizmoAdaptor(generatedClassProducer, true);
        for (AnnotationInstance instance : index.getAnnotations(LangChain4jDotNames.REGISTER_AI_SERVICES)) {
            if (instance.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue; // should never happen
            }
            ClassInfo declarativeAiServiceClassInfo = instance.target().asClass();

            DotName chatLanguageModelSupplierClassDotName = getSupplierDotName(instance.value("chatLanguageModelSupplier"),
                    LangChain4jDotNames.BEAN_CHAT_MODEL_SUPPLIER,
                    supplierDotName -> validateSupplierAndRegisterForReflection(
                            supplierDotName,
                            index,
                            reflectiveClassProducer));

            DotName streamingChatLanguageModelSupplierClassDotName = getSupplierDotName(
                    instance.value("streamingChatLanguageModelSupplier"),
                    LangChain4jDotNames.BEAN_STREAMING_CHAT_MODEL_SUPPLIER,
                    supplierDotName -> validateSupplierAndRegisterForReflection(
                            supplierDotName,
                            index,
                            reflectiveClassProducer));

            String chatModelName = chatModelName(instance, chatLanguageModelSupplierClassDotName,
                    streamingChatLanguageModelSupplierClassDotName, chatModelNames);

            boolean customRetrievalAugmentorSupplierClassIsABean = false;
            DotName retrievalAugmentorSupplierClassName = BEAN_IF_EXISTS_RETRIEVAL_AUGMENTOR_SUPPLIER;
            AnnotationValue retrievalAugmentorSupplierValue = instance.value("retrievalAugmentor");
            if (retrievalAugmentorSupplierValue != null && !BEAN_IF_EXISTS_RETRIEVAL_AUGMENTOR_SUPPLIER
                    .equals(retrievalAugmentorSupplierValue.asClass().name())) {
                if (NO_RETRIEVAL_AUGMENTOR_SUPPLIER.equals(retrievalAugmentorSupplierValue.asClass().name())) {
                    retrievalAugmentorSupplierClassName = null;
                } else {
                    retrievalAugmentorSupplierClassName = retrievalAugmentorSupplierValue.asClass().name();
                    // if the supplier is not a CDI bean, make sure can build an instance
                    BuiltinScope declaredScope = BuiltinScope
                            .from(index.getClassByName(retrievalAugmentorSupplierClassName));
                    if (declaredScope != null) {
                        customRetrievalAugmentorSupplierClassIsABean = true;
                    } else {
                        validateSupplierAndRegisterForReflection(retrievalAugmentorSupplierClassName, index,
                                reflectiveClassProducer);
                    }
                }
            }

            DotName moderationModelSupplierClassName = LangChain4jDotNames.BEAN_IF_EXISTS_MODERATION_MODEL_SUPPLIER;
            AnnotationValue moderationModelSupplierValue = instance.value("moderationModelSupplier");
            if (moderationModelSupplierValue != null) {
                moderationModelSupplierClassName = moderationModelSupplierValue.asClass().name();
                validateSupplierAndRegisterForReflection(moderationModelSupplierClassName, index, reflectiveClassProducer);
            }

            DotName toolProviderClassName = LangChain4jDotNames.BEAN_IF_EXISTS_TOOL_PROVIDER_SUPPLIER;
            AnnotationValue toolProviderValue = instance.value("toolProviderSupplier");
            if (toolProviderValue != null) {
                if (LangChain4jDotNames.NO_TOOL_PROVIDER_SUPPLIER.equals(toolProviderValue.asClass().name())) {
                    toolProviderClassName = null;
                } else {
                    toolProviderClassName = toolProviderValue.asClass().name();
                    validateSupplierAndRegisterForReflection(toolProviderClassName, index, reflectiveClassProducer);
                    toolProviderInfos.add(new ToolProviderInfo(toolProviderClassName.toString(),
                            declarativeAiServiceClassInfo.simpleName()));
                }
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
                    if (moderationModelSupplierClassName.equals(LangChain4jDotNames.BEAN_IF_EXISTS_MODERATION_MODEL_SUPPLIER)) {
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
            DotName chatMemoryProviderSupplierClassDotName = chatMemoryProviderSupplierClassDotName(reflectiveClassProducer,
                    instance, index);
            if (!tools.isEmpty() && chatMemoryProviderSupplierClassDotName == null) {
                throw new IllegalArgumentException("Tool usage requires chat memory. Offending AiService is '"
                        + declarativeAiServiceClassInfo.name() + "'");
            }
            declarativeAiServiceProducer.produce(
                    new DeclarativeAiServiceBuildItem(
                            declarativeAiServiceClassInfo,
                            chatLanguageModelSupplierClassDotName,
                            streamingChatLanguageModelSupplierClassDotName,
                            tools,
                            chatMemoryProviderSupplierClassDotName,
                            retrievalAugmentorSupplierClassName,
                            customRetrievalAugmentorSupplierClassIsABean,
                            moderationModelSupplierClassName,
                            imageModelSupplierClassName(reflectiveClassProducer, instance, index),
                            determineChatMemorySeeder(declarativeAiServiceClassInfo, generatedClassOutput),
                            cdiScope(customScopes, declarativeAiServiceClassInfo),
                            chatModelName,
                            moderationModelName,
                            imageModelName,
                            toolProviderClassName,
                            beanName(declarativeAiServiceClassInfo),
                            toolHallucinationStrategy(instance)));
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

    private static String chatModelName(AnnotationInstance instance, DotName chatLanguageModelSupplierClassDotName,
            DotName streamingChatLanguageModelSupplierClassDotName, Set<String> chatModelNames) {
        String chatModelName = NamedConfigUtil.DEFAULT_NAME;
        if (chatLanguageModelSupplierClassDotName == null && streamingChatLanguageModelSupplierClassDotName == null) {
            AnnotationValue modelNameValue = instance.value("modelName");
            if (modelNameValue != null) {
                String modelNameValueStr = modelNameValue.asString();
                if ((modelNameValueStr != null) && !modelNameValueStr.isEmpty()) {
                    chatModelName = modelNameValueStr;
                }
            }
            chatModelNames.add(chatModelName);
        }
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

    private DotName imageModelSupplierClassName(BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            AnnotationInstance instance, IndexView index) {
        DotName imageModelSupplierClassName = LangChain4jDotNames.BEAN_IF_EXISTS_IMAGE_MODEL_SUPPLIER;
        AnnotationValue imageModelSupplierValue = instance.value("imageModelSupplier");
        if (imageModelSupplierValue != null) {
            imageModelSupplierClassName = imageModelSupplierValue.asClass().name();
            validateSupplierAndRegisterForReflection(imageModelSupplierClassName, index, reflectiveClassProducer);
        }
        return imageModelSupplierClassName;
    }

    private DotName chatMemoryProviderSupplierClassDotName(BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            AnnotationInstance instance, IndexView index) {
        // the default value depends on whether tools exists or not - if they do, then we require a ChatMemoryProvider bean
        DotName chatMemoryProviderSupplierClassDotName = LangChain4jDotNames.BEAN_CHAT_MEMORY_PROVIDER_SUPPLIER;
        AnnotationValue chatMemoryProviderSupplierValue = instance.value("chatMemoryProviderSupplier");
        if (chatMemoryProviderSupplierValue != null) {
            chatMemoryProviderSupplierClassDotName = chatMemoryProviderSupplierValue.asClass().name();
            if (chatMemoryProviderSupplierClassDotName.equals(
                    LangChain4jDotNames.NO_CHAT_MEMORY_PROVIDER_SUPPLIER)) {
                chatMemoryProviderSupplierClassDotName = null;
            } else if (!chatMemoryProviderSupplierClassDotName
                    .equals(LangChain4jDotNames.BEAN_CHAT_MEMORY_PROVIDER_SUPPLIER)) {
                validateSupplierAndRegisterForReflection(chatMemoryProviderSupplierClassDotName, index,
                        reflectiveClassProducer);
            }
        }
        return chatMemoryProviderSupplierClassDotName;
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

    private static DotName toolHallucinationStrategy(AnnotationInstance instance) {
        AnnotationValue toolHallucinationStrategyInstance = instance.value("toolHallucinationStrategy");
        if (toolHallucinationStrategyInstance != null) {
            return toolHallucinationStrategyInstance.asClass().name();
        }
        return null;
    }

    private DotName getSupplierDotName(
            AnnotationValue instanceAnnotation,
            DotName supplierDotName,
            Consumer<DotName> validator) {
        DotName dotName = null;
        if (instanceAnnotation != null) {
            dotName = instanceAnnotation.asClass().name();
            if (dotName.equals(supplierDotName)) {
                // this is the case where the default was set, so we just ignore it
                dotName = null;
            } else {
                validator.accept(dotName);
            }
        }
        return dotName;
    }

    private void validateSupplierAndRegisterForReflection(DotName supplierDotName, IndexView index,
            BuildProducer<ReflectiveClassBuildItem> producer) {
        ClassInfo classInfo = index.getClassByName(supplierDotName);
        if (classInfo == null) {
            log.warn("'" + supplierDotName.toString() + "' cannot be indexed"); // TODO: maybe this should be an error
            return;
        }

        if (!classInfo.hasNoArgsConstructor()) {
            throw new IllegalConfigurationException(
                    "Class '" + supplierDotName.toString() + "' which must contain a no-args constructor.");
        }

        producer.produce(ReflectiveClassBuildItem.builder(supplierDotName.toString()).constructors(true).build());
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
        Set<DotName> allToolNames = new HashSet<>();
        Set<DotName> allToolProviders = new HashSet<>();
        Set<DotName> allToolHallucinationStrategies = new HashSet<>();

        for (DeclarativeAiServiceBuildItem bi : declarativeAiServiceItems) {
            ClassInfo declarativeAiServiceClassInfo = bi.getServiceClassInfo();
            String serviceClassName = declarativeAiServiceClassInfo.name().toString();

            String chatLanguageModelSupplierClassName = (bi.getChatLanguageModelSupplierClassDotName() != null
                    ? bi.getChatLanguageModelSupplierClassDotName().toString()
                    : null);

            String streamingChatLanguageModelSupplierClassName = (bi.getStreamingChatLanguageModelSupplierClassDotName() != null
                    ? bi.getStreamingChatLanguageModelSupplierClassDotName().toString()
                    : null);

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

            String toolProviderSupplierClassName = (bi.getToolProviderClassDotName() != null
                    ? bi.getToolProviderClassDotName().toString()
                    : null);

            String toolHallucinationStrategyClassName = null;
            if (bi.getToolHallucinationStrategyClassDotName() != null) {
                toolHallucinationStrategyClassName = bi.getToolHallucinationStrategyClassDotName().toString();
                allToolHallucinationStrategies.add(bi.getToolHallucinationStrategyClassDotName());
            }

            String chatMemoryProviderSupplierClassName = bi.getChatMemoryProviderSupplierClassDotName() != null
                    ? bi.getChatMemoryProviderSupplierClassDotName().toString()
                    : null;

            String retrievalAugmentorSupplierClassName = bi.getRetrievalAugmentorSupplierClassDotName() != null
                    ? bi.getRetrievalAugmentorSupplierClassDotName().toString()
                    : null;

            String moderationModelSupplierClassName = (bi.getModerationModelSupplierDotName() != null
                    ? bi.getModerationModelSupplierDotName().toString()
                    : null);

            String imageModelSupplierClassName = (bi.getImageModelSupplierDotName() != null
                    ? bi.getImageModelSupplierDotName().toString()
                    : null);

            String chatMemorySeederClassName = (bi.getChatMemorySeederClassDotName() != null
                    ? bi.getChatMemorySeederClassDotName().toString()
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
                boolean isMultiString = false;
                if (method.returnType().kind() == Type.Kind.PARAMETERIZED_TYPE) {
                    Type multiType = method.returnType().asParameterizedType().arguments().get(0);
                    if (DotNames.STRING.equals(multiType.name())) {
                        isMultiString = true;
                    }
                }
                if (!isMultiString) {
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
            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(QuarkusAiServiceContext.class)
                    .forceApplicationClass()
                    .createWith(recorder.createDeclarativeAiService(
                            new DeclarativeAiServiceCreateInfo(
                                    serviceClassName,
                                    chatLanguageModelSupplierClassName,
                                    streamingChatLanguageModelSupplierClassName,
                                    toolToQualifierMap,
                                    toolProviderSupplierClassName,
                                    chatMemoryProviderSupplierClassName,
                                    retrievalAugmentorSupplierClassName,
                                    moderationModelSupplierClassName,
                                    imageModelSupplierClassName,
                                    chatMemorySeederClassName,
                                    chatModelName,
                                    moderationModelName,
                                    bi.getImageModelName(),
                                    injectStreamingChatModelBean,
                                    injectModerationModelBean,
                                    injectImageModel,
                                    toolHallucinationStrategyClassName)))
                    .setRuntimeInit()
                    .addQualifier()
                    .annotation(LangChain4jDotNames.QUARKUS_AI_SERVICE_CONTEXT_QUALIFIER).addValue("value", serviceClassName)
                    .done()
                    .scope(Dependent.class);

            boolean hasChatModelSupplier = chatLanguageModelSupplierClassName == null
                    && streamingChatLanguageModelSupplierClassName == null;
            if (hasChatModelSupplier && !selectedChatModelProvider.isEmpty()) {
                if (NamedConfigUtil.isDefault(chatModelName)) {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.CHAT_MODEL));
                    if (injectStreamingChatModelBean) {
                        configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.STREAMING_CHAT_MODEL));
                        needsStreamingChatModelBean = true;
                    }
                } else {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.CHAT_MODEL),
                            AnnotationInstance.builder(ModelName.class).add("value", chatModelName).build());

                    if (injectStreamingChatModelBean) {
                        configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.STREAMING_CHAT_MODEL),
                                AnnotationInstance.builder(ModelName.class).add("value", chatModelName).build());
                        needsStreamingChatModelBean = true;
                    }
                }
                needsChatModelBean = true;
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

            if (LangChain4jDotNames.BEAN_CHAT_MEMORY_PROVIDER_SUPPLIER.toString().equals(chatMemoryProviderSupplierClassName)) {
                configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.CHAT_MEMORY_PROVIDER));
                needsChatMemoryProviderBean = true;
            }

            if (LangChain4jDotNames.BEAN_IF_EXISTS_RETRIEVAL_AUGMENTOR_SUPPLIER.toString()
                    .equals(retrievalAugmentorSupplierClassName)) {
                // Use a CDI bean of type `RetrievalAugmentor` if one exists, otherwise
                // don't use an augmentor.
                configurator.addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                        new Type[] { ClassType.create(LangChain4jDotNames.RETRIEVAL_AUGMENTOR) }, null));
                needsRetrievalAugmentorBean = true;
            } else {
                if (retrievalAugmentorSupplierClassName != null) {
                    // Use the provided `Supplier<RetrievalAugmentor>`. If
                    // the provided supplier, is a CDI bean, use it as such
                    // and declare an injection point for it here. If it's
                    // not a CDI bean, the recorder will call its no-arg
                    // constructor to obtain an instance.
                    if (bi.isCustomRetrievalAugmentorSupplierClassIsABean()) {
                        configurator.addInjectionPoint(ClassType.create(retrievalAugmentorSupplierClassName));
                        unremovableProducer
                                .produce(UnremovableBeanBuildItem.beanClassNames(retrievalAugmentorSupplierClassName));
                    }
                }
            }

            if (LangChain4jDotNames.BEAN_IF_EXISTS_MODERATION_MODEL_SUPPLIER.toString()
                    .equals(moderationModelSupplierClassName) && injectModerationModelBean) {

                if (NamedConfigUtil.isDefault(moderationModelName)) {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.MODERATION_MODEL));

                } else {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.MODERATION_MODEL),
                            AnnotationInstance.builder(ModelName.class).add("value", moderationModelName).build());
                }
                needsModerationModelBean = true;
            }

            if (LangChain4jDotNames.BEAN_IF_EXISTS_IMAGE_MODEL_SUPPLIER.toString()
                    .equals(imageModelSupplierClassName) && injectImageModel) {

                if (NamedConfigUtil.isDefault(chatModelName)) {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.IMAGE_MODEL));

                } else {
                    configurator.addInjectionPoint(ClassType.create(LangChain4jDotNames.IMAGE_MODEL),
                            AnnotationInstance.builder(ModelName.class).add("value", chatModelName).build());
                }
                needsImageModelBean = true;
            }

            if (RegisterAiService.BeanIfExistsToolProviderSupplier.class.getName()
                    .equals(toolProviderSupplierClassName)) {
                configurator.addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                        new Type[] { ClassType.create(LangChain4jDotNames.TOOL_PROVIDER) }, null));
                needsToolProviderBean = true;
            } else if (!RegisterAiService.NoToolProviderSupplier.class.getName()
                    .equals(toolProviderSupplierClassName) && toolProviderSupplierClassName != null) {
                DotName toolProvider = DotName.createSimple(toolProviderSupplierClassName);
                configurator.addInjectionPoint(ClassType.create(toolProvider));
                allToolProviders.add(toolProvider);
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
        if (!allToolNames.isEmpty()) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(allToolNames));
        }
        if (!allToolHallucinationStrategies.isEmpty()) {
            unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(allToolHallucinationStrategies));
        }
    }

    @BuildStep
    public void markUsedGuardRailsUnremovable(List<AiServicesMethodBuildItem> methods,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer) {
        for (AiServicesMethodBuildItem method : methods) {
            List<String> list = new ArrayList<>(method.getOutputGuardrails());
            list.addAll(method.getInputGuardrails());
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
     */
    public boolean detectAiServiceMethodThanNeedToBeDispatchedOnWorkerThread(
            MethodInfo method,
            List<String> associatedTools,
            List<ToolMethodBuildItem> tools) {
        boolean reactive = method.returnType().name().equals(DotNames.UNI)
                || method.returnType().name().equals(DotNames.COMPLETION_STAGE)
                || method.returnType().name().equals(DotNames.MULTI);

        boolean requireSwitchToWorkerThread = false;

        if (associatedTools.isEmpty()) {
            // No tools, no need to dispatch
            return false;
        }

        if (!reactive) {
            // We are already on a thread we can block.
            return false;
        }

        // We need to find if any of the tools that could be used by the method is requiring a blocking execution
        for (String classname : associatedTools) {
            // Look for the tool in the list of tools
            boolean found = false;
            for (ToolMethodBuildItem tool : tools) {
                if (tool.getDeclaringClassName().equals(classname)) {
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

    @BuildStep
    public void validateGuardrails(SynthesisFinishedBuildItem synthesisFinished,
            List<AiServicesMethodBuildItem> methods,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> errors) {

        for (AiServicesMethodBuildItem method : methods) {
            List<String> list = new ArrayList<>(method.getOutputGuardrails());
            list.addAll(method.getInputGuardrails());
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

                // Check that the method have output guardrails
                if (method.getOutputGuardrails().isEmpty()) {
                    errors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                            new DeploymentException("OutputGuardrailAccumulator used without OutputGuardrails in method `%s.%s`"
                                    .formatted(method.getMethodInfo().declaringClass().toString(),
                                            method.getMethodInfo().name()))));
                }
            }
        }
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
            List<ToolQualifierProvider.BuildItem> toolQualifierProviderItems) {

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
        addIfacesWithMessageAnns(index, detectedForCreate);
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
                JandexUtil.getAllSuperinterfaces(iface, index).forEach(ci -> allMethods.addAll(ci.methods()));

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
                        .interfaces(ifaceName, ChatMemoryRemovable.class.getName());
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
                        if (matchingBI.getBeanName().isPresent()) {
                            classCreator.addAnnotation(
                                    AnnotationInstance.builder(NAMED).add("value", matchingBI.getBeanName().get()).build());
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
                                tools, toolQualifierProviderItems);
                        if (!methodCreateInfo.getToolClassInfo().isEmpty()) {
                            if ((matchingBI != null)
                                    && matchingBI.getChatMemoryProviderSupplierClassDotName() == null) {
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
                                    methodCreateInfo.getInputGuardrailsClassNames(),
                                    methodCreateInfo.getOutputGuardrailsClassNames(),
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

                    {
                        MethodCreator mc = classCreator.getMethodCreator(
                                MethodDescriptor.ofMethod(implClassName, "remove", void.class, Object[].class));
                        ResultHandle contextHandle = mc.readInstanceField(contextField, mc.getThis());
                        mc.invokeVirtualMethod(QUARKUS_AI_SERVICES_CONTEXT_REMOVE_CHAT_MEMORY_IDS, contextHandle,
                                mc.getMethodParam(0));
                        mc.returnVoid();
                    }

                }
                perClassMetadata.put(ifaceName, new AiServiceClassCreateInfo(perMethodMetadata, implClassName));
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
                containerHandle, mc.loadClassFromTCCL(className),
                mc.newArray(Annotation.class, 0));
        return mc.invokeInterfaceMethod(MethodDescriptor.ofMethod(InstanceHandle.class, "get", Object.class), instanceHandle);
    }

    private String createMethodId(MethodInfo methodInfo) {
        return methodInfo.name() + '('
                + Arrays.toString(methodInfo.parameters().stream().map(mp -> mp.type().name().toString()).toArray()) + ')';
    }

    private void addIfacesWithMessageAnns(IndexView index, Set<String> detectedForCreate) {
        List<DotName> annotations = List.of(LangChain4jDotNames.SYSTEM_MESSAGE, LangChain4jDotNames.USER_MESSAGE,
                LangChain4jDotNames.MODERATE);
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
            List<ToolQualifierProvider.BuildItem> toolQualifierProviders) {
        validateReturnType(method);

        boolean requiresModeration = method.hasAnnotation(LangChain4jDotNames.MODERATE);
        java.lang.reflect.Type returnType = javaLangReturnType(method);

        List<MethodParameterInfo> params = method.parameters();

        // TODO give user ability to provide custom OutputParser
        String outputFormatInstructions = "";
        Optional<JsonSchema> structuredOutputSchema = Optional.empty();
        if (!returnType.equals(Multi.class)) {
            outputFormatInstructions = SERVICE_OUTPUT_PARSER.outputFormatInstructions(returnType);
        }

        List<TemplateParameterInfo> templateParams = gatherTemplateParamInfo(params, allowedPredicates, ignoredPredicates);
        Optional<AiServiceMethodCreateInfo.TemplateInfo> systemMessageInfo = gatherSystemMessageInfo(method, templateParams);
        AiServiceMethodCreateInfo.UserMessageInfo userMessageInfo = gatherUserMessageInfo(method, templateParams,
                systemMessageInfo);

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

        List<String> outputGuardrails = AiServicesMethodBuildItem.gatherGuardrails(method, OUTPUT_GUARDRAILS);
        List<String> inputGuardrails = AiServicesMethodBuildItem.gatherGuardrails(method, INPUT_GUARDRAILS);

        String accumulatorClassName = AiServicesMethodBuildItem.gatherAccumulator(method);

        String responseAugmenterClassName = AiServicesMethodBuildItem.gatherResponseAugmenter(method);

        //  Detect if tools execution may block the caller thread.
        boolean switchToWorkerThreadForToolExecution = detectIfToolExecutionRequiresAWorkerThread(method, tools,
                methodToolClassInfo.keySet());

        return new AiServiceMethodCreateInfo(method.declaringClass().name().toString(), method.name(), systemMessageInfo,
                userMessageInfo, memoryIdParamPosition, requiresModeration,
                returnTypeSignature(method.returnType(), new TypeArgMapper(method.declaringClass(), index)),
                overrideChatModelParamPosition, metricsTimedInfo, metricsCountedInfo, spanInfo, responseSchemaInfo,
                methodToolClassInfo, methodMcpClientNames, switchToWorkerThreadForToolExecution, inputGuardrails,
                outputGuardrails, accumulatorClassName, responseAugmenterClassName);
    }

    private Optional<JsonSchema> jsonSchemaFrom(java.lang.reflect.Type returnType) {
        if (isMulti(returnType)) {
            return Optional.empty();
        }
        return JsonSchemas.jsonSchemaFrom(returnType);
    }

    private boolean detectIfToolExecutionRequiresAWorkerThread(MethodInfo method, List<ToolMethodBuildItem> tools,
            Collection<String> methodToolClassNames) {
        List<String> allTools = new ArrayList<>(methodToolClassNames);
        // We need to combine it with the tools that are registered globally - unfortunately, we don't have access to the AI service here, so, re-parsing.
        AnnotationInstance annotation = method.declaringClass().annotation(REGISTER_AI_SERVICES);
        if (annotation != null) {
            AnnotationValue value = annotation.value("tools");
            if (value != null) {
                allTools.addAll(Arrays.stream(value.asClassArray()).map(t -> t.name().toString()).toList());
            }
        }
        return detectAiServiceMethodThanNeedToBeDispatchedOnWorkerThread(method, allTools, tools);
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

    private String returnTypeSignature(Type returnType, TypeArgMapper typeArgMapper) {
        return AsmUtil.getSignature(returnType, typeArgMapper);
    }

    private List<TemplateParameterInfo> gatherTemplateParamInfo(List<MethodParameterInfo> params,
            Collection<Predicate<AnnotationInstance>> allowedPredicates,
            Collection<Predicate<AnnotationInstance>> ignoredPredicates) {
        if (params.isEmpty()) {
            return Collections.emptyList();
        }

        List<TemplateParameterInfo> templateParams = new ArrayList<>();
        for (MethodParameterInfo param : params) {

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
            log.warn(
                    "The application has been compiled without the '-parameters' being set flag on javac. Make sure your build tool is configured to pass this flag to javac, otherwise Quarkus LangChain4j is unlikely to work properly without it.");
        }

        if ((templateParams.size() == 1) && (params.size() == 1)) {
            // the special 'it' param is supported when the method only has one parameter
            templateParams.add(new TemplateParameterInfo(0, "it"));
        }

        return templateParams;
    }

    private boolean isParameterAllowedAsTemplateVariable(
            MethodParameterInfo param, Collection<Predicate<AnnotationInstance>> allowedPredicates,
            Collection<Predicate<AnnotationInstance>> ignoredPredicates) {

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

    private AiServiceMethodCreateInfo.UserMessageInfo gatherUserMessageInfo(MethodInfo method,
            List<TemplateParameterInfo> templateParams,
            Optional<AiServiceMethodCreateInfo.TemplateInfo> systemMessageInfo) {

        Optional<Integer> userNameParamPosition = method.annotations(LangChain4jDotNames.USER_NAME).stream().filter(
                IS_METHOD_PARAMETER_ANNOTATION).map(METHOD_PARAMETER_POSITION_FUNCTION).findFirst();
        Optional<Integer> imageParamPosition = determineImageParamPosition(method);
        if (imageParamPosition.isPresent()) {
            MethodParameterInfo imageUrlParam = method.parameters().get(imageParamPosition.get());
            validateImageUrlParam(imageUrlParam);
        }
        Optional<Integer> pdfParamPosition = determinePdfParamPosition(method);
        if (pdfParamPosition.isPresent()) {
            MethodParameterInfo pdfUrlParam = method.parameters().get(pdfParamPosition.get());
            validatePdfUrlParam(pdfUrlParam);
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
                    userNameParamPosition, imageParamPosition, pdfParamPosition);
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
                            userNameParamPosition, imageParamPosition, pdfParamPosition);
                } else {
                    return AiServiceMethodCreateInfo.UserMessageInfo.fromMethodParam(
                            userMessageOnMethodParam.get().target().asMethodParameter().position(),
                            userNameParamPosition, imageParamPosition, pdfParamPosition);
                }
            } else {
                int numOfMethodParamsUsedInSystemMessage = 0;
                if (systemMessageInfo.isPresent() && systemMessageInfo.get().text().isPresent()) {
                    Set<String> templateParamNames = TemplateUtil.parts(systemMessageInfo.get().text().get()).stream()
                            .flatMap(l -> l.stream().map(
                                    Expression.Part::getName))
                            .collect(Collectors.toSet());
                    for (MethodParameterInfo parameter : method.parameters()) {
                        if (templateParamNames.contains(parameter.name())) {
                            numOfMethodParamsUsedInSystemMessage++;
                        }
                    }
                }
                if (numOfMethodParamsUsedInSystemMessage != method.parametersCount()) {
                    if (method.parametersCount() == 0) {
                        throw illegalConfigurationForMethod("Method should have at least one argument", method);
                    }
                    if (method.parametersCount() == 1) {
                        return AiServiceMethodCreateInfo.UserMessageInfo.fromMethodParam(0, userNameParamPosition,
                                imageParamPosition, pdfParamPosition);
                    }
                    throw illegalConfigurationForMethod(
                            "For methods with multiple parameters, each parameter must be annotated with @V (or match an template parameter by name), @UserMessage, @UserName or @MemoryId",
                            method);
                } else {
                    // all method parameters are present in the system message, so there is no user message
                    return new AiServiceMethodCreateInfo.UserMessageInfo(Optional.empty(), Optional.empty(), Optional.empty(),
                            Optional.empty(), Optional.empty());
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
        // we don't need @ImageUrl if the parameter is of type Image
        return method.parameters().stream().filter(pi -> pi.type().name().equals(LangChain4jDotNames.IMAGE))
                .map(pi -> (int) pi.position()).findFirst();
    }

    private void validateImageUrlParam(MethodParameterInfo param) {
        if (param == null) {
            throw new IllegalArgumentException("Unhandled @ImageUrl annotation");
        }
        Type type = param.type();
        DotName typeName = type.name();
        if (typeName.equals(DotNames.STRING) || typeName.equals(DotNames.URI) || typeName.equals(DotNames.URL)
                || typeName.equals(LangChain4jDotNames.IMAGE)) {
            return;
        }
        throw new IllegalArgumentException("Unhandled @ImageUrl type '" + type.name() + "'");
    }

    private static Optional<Integer> determinePdfParamPosition(MethodInfo method) {
        Optional<Integer> result = method.annotations(LangChain4jDotNames.PDF_URL).stream().filter(
                IS_METHOD_PARAMETER_ANNOTATION).map(METHOD_PARAMETER_POSITION_FUNCTION).findFirst();
        if (result.isPresent()) {
            return result;
        }
        // we don't need @PdfUrl if the parameter is of type PdfFile
        return method.parameters().stream().filter(pi -> pi.type().name().equals(LangChain4jDotNames.PDF_FILE))
                .map(pi -> (int) pi.position()).findFirst();
    }

    private void validatePdfUrlParam(MethodParameterInfo param) {
        if (param == null) {
            throw new IllegalArgumentException("Unhandled @PdfUrl annotation");
        }
        Type type = param.type();
        DotName typeName = type.name();
        if (typeName.equals(DotNames.STRING) || typeName.equals(DotNames.URI) || typeName.equals(DotNames.URL)
                || typeName.equals(LangChain4jDotNames.PDF_FILE)) {
            return;
        }
        throw new IllegalArgumentException("Unhandled @PdfUrl type '" + type.name() + "'");
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
        // Using the class name to keep the McpToolBox annotation in the mcp module
        AnnotationInstance mcpToolBoxInstance = method.declaredAnnotation("io.quarkiverse.langchain4j.mcp.runtime.McpToolBox");
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
