package io.quarkiverse.langchain4j.deployment;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.ARRAY;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.BOOLEAN;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.INTEGER;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.NUMBER;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.OBJECT;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.STRING;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.description;
import static dev.langchain4j.agent.tool.JsonSchemaProperty.enums;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.output.structured.Description;
import io.quarkiverse.langchain4j.runtime.ToolsRecorder;
import io.quarkiverse.langchain4j.runtime.prompt.Mappable;
import io.quarkiverse.langchain4j.runtime.tool.ToolInvoker;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;
import io.quarkiverse.langchain4j.runtime.tool.ToolParametersObjectSubstitution;
import io.quarkiverse.langchain4j.runtime.tool.ToolSpanWrapper;
import io.quarkiverse.langchain4j.runtime.tool.ToolSpecificationObjectSubstitution;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.GeneratedClassGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.recording.RecorderContext;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

public class ToolProcessor {

    private static final DotName TOOL = DotName.createSimple(Tool.class);
    private static final DotName TOOL_MEMORY_ID = DotName.createSimple(ToolMemoryId.class);
    private static final DotName P = DotName.createSimple(dev.langchain4j.agent.tool.P.class);
    private static final MethodDescriptor METHOD_METADATA_CTOR = MethodDescriptor
            .ofConstructor(ToolInvoker.MethodMetadata.class, boolean.class, Map.class, Integer.class);
    private static final MethodDescriptor HASHMAP_CTOR = MethodDescriptor.ofConstructor(HashMap.class);
    public static final MethodDescriptor MAP_PUT = MethodDescriptor.ofMethod(Map.class, "put", Object.class, Object.class,
            Object.class);
    private static final Logger log = Logger.getLogger(ToolProcessor.class);

    public static final DotName OPTIONAL = DotName.createSimple("java.util.Optional");
    public static final DotName OPTIONAL_INT = DotName.createSimple("java.util.OptionalInt");
    public static final DotName OPTIONAL_LONG = DotName.createSimple("java.util.OptionalLong");
    public static final DotName OPTIONAL_DOUBLE = DotName.createSimple("java.util.OptionalDouble");

    private static final DotName DATE = DotName.createSimple("java.util.Date");
    private static final DotName LOCAL_DATE = DotName.createSimple("java.time.LocalDate");
    private static final DotName LOCAL_DATE_TIME = DotName.createSimple("java.time.LocalDateTime");
    private static final DotName OFFSET_DATE_TIME = DotName.createSimple("java.time.OffsetDateTime");

    @BuildStep
    public void telemetry(Capabilities capabilities, BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {
        var addOpenTelemetrySpan = capabilities.isPresent(Capability.OPENTELEMETRY_TRACER);
        if (addOpenTelemetrySpan) {
            additionalBeanProducer.produce(AdditionalBeanBuildItem.builder().addBeanClass(ToolSpanWrapper.class).build());
        }
    }

    @BuildStep
    public void handleTools(CombinedIndexBuildItem indexBuildItem,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<BytecodeTransformerBuildItem> transformerProducer,
            BuildProducer<GeneratedClassBuildItem> generatedClassProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> validation,
            BuildProducer<ToolsMetadataBeforeRemovalBuildItem> toolsMetadataProducer) {

        IndexView index = indexBuildItem.getIndex();

        Collection<AnnotationInstance> instances = index.getAnnotations(TOOL);
        Map<String, List<ToolMethodCreateInfo>> metadata = new HashMap<>();

        List<String> generatedInvokerClasses = new ArrayList<>();
        List<String> generatedArgumentMapperClasses = new ArrayList<>();

        if (!instances.isEmpty()) {
            ClassOutput classOutput = new GeneratedClassGizmoAdaptor(generatedClassProducer, true);

            Map<DotName, List<MethodInfo>> methodsPerClass = new HashMap<>();

            for (AnnotationInstance instance : instances) {
                if (instance.target().kind() != AnnotationTarget.Kind.METHOD) {
                    continue;
                }

                MethodInfo methodInfo = instance.target().asMethod();
                ClassInfo classInfo = methodInfo.declaringClass();
                if (classInfo.isInterface() || Modifier.isAbstract(classInfo.flags())) {
                    validation.produce(
                            new ValidationPhaseBuildItem.ValidationErrorBuildItem(new IllegalStateException(
                                    "@Tool is only supported on non-abstract classes, all other usages are ignored. Offending method is '"
                                            + methodInfo.declaringClass().name().toString() + "#" + methodInfo.name() + "'")));
                    continue;
                }

                DotName declaringClassName = classInfo.name();
                methodsPerClass.computeIfAbsent(declaringClassName, (n -> new ArrayList<>())).add(methodInfo);
            }

            boolean validationErrorFound = false;
            Map<String, ClassInfo> discoveredTools = new HashMap<>();
            for (var entry : methodsPerClass.entrySet()) {
                DotName className = entry.getKey();

                List<MethodInfo> toolMethods = entry.getValue();
                List<MethodInfo> privateMethods = new ArrayList<>();
                for (MethodInfo toolMethod : toolMethods) {
                    // Validation
                    // - Must not have another tool with the same method name
                    // - Must have at least one parameter
                    if (discoveredTools.containsKey(toolMethod.name())) {
                        validation.produce(
                                new ValidationPhaseBuildItem.ValidationErrorBuildItem(new IllegalStateException(
                                        "A tool with the name '" + toolMethod.name() + "' from class '"
                                                + className + "' is already declared in class '"
                                                + discoveredTools.get(toolMethod.name())
                                                + "'. Tools method name must be unique.")));
                        validationErrorFound = true;
                        continue;
                    }
                    discoveredTools.put(toolMethod.name(), toolMethod.declaringClass());

                    if (Modifier.isPrivate(toolMethod.flags())) {
                        privateMethods.add(toolMethod);
                    }
                }
                if (!privateMethods.isEmpty()) {
                    transformerProducer.produce(new BytecodeTransformerBuildItem(className.toString(),
                            new RemovePrivateFromMethodsVisitor(privateMethods)));
                }

                if (validationErrorFound) {
                    return;
                }

                for (MethodInfo toolMethod : toolMethods) {
                    AnnotationInstance instance = toolMethod.annotation(TOOL);
                    boolean ignoreToolMethod = ignoreToolMethod(toolMethod, index);
                    if (ignoreToolMethod) {
                        continue;
                    } else {
                        // The WebSearchTool class isn't a CDI bean, so if
                        // we consider it as a tool, we have to also turn it into one
                        if (LangChain4jDotNames.WEB_SEARCH_TOOL.equals(className)) {
                            additionalBeanProducer.produce(AdditionalBeanBuildItem.builder()
                                    .addBeanClass(className.toString())
                                    .setUnremovable().build());
                        }
                    }

                    AnnotationValue nameValue = instance.value("name");
                    AnnotationValue descriptionValue = instance.value();

                    String toolName = getToolName(nameValue, toolMethod);
                    String toolDescription = getToolDescription(descriptionValue);

                    ToolSpecification.Builder builder = ToolSpecification.builder()
                            .name(toolName)
                            .description(toolDescription);

                    MethodParameterInfo memoryIdParameter = null;
                    for (MethodParameterInfo parameter : toolMethod.parameters()) {
                        if (parameter.hasAnnotation(TOOL_MEMORY_ID)) {
                            memoryIdParameter = parameter;
                            continue;
                        }

                        AnnotationInstance pInstance = parameter.annotation(P);
                        if (pInstance != null && pInstance.value("required") != null
                                && !pInstance.value("required").asBoolean()) {
                            builder.addOptionalParameter(parameter.name(), toJsonSchemaProperties(parameter, index));
                        } else {
                            builder.addParameter(parameter.name(), toJsonSchemaProperties(parameter, index));
                        }
                    }

                    Map<String, Integer> nameToParamPosition = toolMethod.parameters().stream().collect(
                            Collectors.toMap(MethodParameterInfo::name, i -> Integer.valueOf(i.position())));

                    String methodSignature = createUniqueSignature(toolMethod);

                    String invokerClassName = generateInvoker(toolMethod, classOutput, nameToParamPosition,
                            memoryIdParameter != null ? memoryIdParameter.position() : null, methodSignature);
                    generatedInvokerClasses.add(invokerClassName);
                    String argumentMapperClassName = generateArgumentMapper(toolMethod, classOutput,
                            methodSignature);
                    generatedArgumentMapperClasses.add(argumentMapperClassName);

                    ToolSpecification toolSpecification = builder.build();
                    ToolMethodCreateInfo methodCreateInfo = new ToolMethodCreateInfo(
                            toolMethod.name(), invokerClassName,
                            toolSpecification, argumentMapperClassName);

                    metadata.computeIfAbsent(className.toString(), (c) -> new ArrayList<>()).add(methodCreateInfo);

                }
            }
        }

        if (!generatedInvokerClasses.isEmpty()) {
            reflectiveClassProducer.produce(ReflectiveClassBuildItem
                    .builder(generatedInvokerClasses.toArray(String[]::new))
                    .constructors(true)
                    .build());
        }
        if (!generatedArgumentMapperClasses.isEmpty()) {
            reflectiveClassProducer.produce(ReflectiveClassBuildItem
                    .builder(generatedArgumentMapperClasses.toArray(String[]::new))
                    .fields(true)
                    .constructors(true)
                    .build());
        }

        toolsMetadataProducer.produce(new ToolsMetadataBeforeRemovalBuildItem(metadata));
    }

    /**
     * Transforms ToolsMetadataBeforeRemovalBuildItem into ToolsMetadataBuildItem by filtering
     * out tools belonging to beans that have been removed by ArC.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public ToolsMetadataBuildItem filterOutRemovedTools(ToolsMetadataBeforeRemovalBuildItem beforeRemoval,
            ValidationPhaseBuildItem validationPhase,
            RecorderContext recorderContext,
            ToolsRecorder recorder) {
        if (beforeRemoval != null) {
            recorderContext.registerSubstitution(ToolSpecification.class, ToolSpecificationObjectSubstitution.Serialized.class,
                    ToolSpecificationObjectSubstitution.class);
            recorderContext.registerSubstitution(ToolParameters.class, ToolParametersObjectSubstitution.Serialized.class,
                    ToolParametersObjectSubstitution.class);
            Map<String, List<ToolMethodCreateInfo>> metadataWithoutRemovedBeans = beforeRemoval.getMetadata().entrySet()
                    .stream()
                    .filter(entry -> validationPhase.getContext().removedBeans().stream()
                            .noneMatch(
                                    retainedBean -> DotName.createSimple(entry.getKey()).equals(retainedBean.getBeanClass())))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            ToolsMetadataBuildItem toolsMetadata = new ToolsMetadataBuildItem(metadataWithoutRemovedBeans);
            recorder.setMetadata(toolsMetadata.getMetadata());
            log.debug("Tool classes before filtering out removed beans: " + beforeRemoval.getMetadata().keySet());
            log.debug("Tool classes after filtering out removed beans: " + toolsMetadata.getMetadata().keySet());
            return toolsMetadata;
        } else {
            return null;
        }
    }

    private boolean ignoreToolMethod(MethodInfo toolMethod, IndexView indexView) {
        ClassInfo declaringClass = toolMethod.declaringClass();
        if (LangChain4jDotNames.WEB_SEARCH_TOOL.equals(declaringClass.name())) {
            // WebSearchTool is included in LangChain4j and is annotated with @Tool
            // However, we can't add this automatically since there is no bean that implements it
            // As a heuristic, we simply ignore it if there is no implementation of WebSearchEngine available
            if (indexView.getAllKnownImplementors(LangChain4jDotNames.WEB_SEARCH_ENGINE).isEmpty()) {
                log.debug("Ignoring tool " + LangChain4jDotNames.WEB_SEARCH_TOOL + "#" + toolMethod.name()
                        + " as there is no implementation of " + LangChain4jDotNames.WEB_SEARCH_ENGINE + " on the classpath");
                return true;
            }
        }
        return false;
    }

    private static String createUniqueSignature(MethodInfo toolMethod) {
        StringBuilder sigBuilder = new StringBuilder();
        sigBuilder.append(toolMethod.name())
                .append(toolMethod.returnType().name().toString());
        for (MethodParameterInfo t : toolMethod.parameters()) {
            sigBuilder.append(t.type().name().toString());
        }
        return sigBuilder.toString();
    }

    private static String getToolName(AnnotationValue nameValue, MethodInfo methodInfo) {
        if (nameValue == null) {
            return methodInfo.name();
        }

        String annotationValue = nameValue.asString();
        if (annotationValue.isEmpty()) {
            return methodInfo.name();
        }
        return annotationValue;
    }

    private String getToolDescription(AnnotationValue descriptionValue) {
        if (descriptionValue == null) {
            return "";
        }
        return String.join("\n", descriptionValue.asStringArray());
    }

    private static String generateInvoker(MethodInfo methodInfo, ClassOutput classOutput,
            Map<String, Integer> nameToParamPosition, Short memoryIdParamPosition, String methodSignature) {
        String implClassName = methodInfo.declaringClass().name() + "$$QuarkusInvoker$" + methodInfo.name() + "_"
                + HashUtil.sha1(methodSignature);
        try (ClassCreator classCreator = ClassCreator.builder()
                .classOutput(classOutput)
                .className(implClassName)
                .interfaces(ToolInvoker.class)
                .build()) {

            MethodCreator invokeMc = classCreator.getMethodCreator(
                    MethodDescriptor.ofMethod(implClassName, "invoke", Object.class, Object.class, Object[].class));

            ResultHandle result;
            if (methodInfo.parametersCount() > 0) {
                List<ResultHandle> argumentHandles = new ArrayList<>(methodInfo.parametersCount());
                for (int i = 0; i < methodInfo.parametersCount(); i++) {
                    argumentHandles.add(invokeMc.readArrayValue(invokeMc.getMethodParam(1), i));
                }
                ResultHandle[] targetMethodHandles = argumentHandles.toArray(new ResultHandle[0]);
                result = invokeMc.invokeVirtualMethod(MethodDescriptor.of(methodInfo), invokeMc.getMethodParam(0),
                        targetMethodHandles);
            } else {
                result = invokeMc.invokeVirtualMethod(MethodDescriptor.of(methodInfo), invokeMc.getMethodParam(0));
            }

            boolean toolReturnsVoid = methodInfo.returnType().kind() == Type.Kind.VOID;
            if (toolReturnsVoid) {
                invokeMc.returnValue(invokeMc.load("Success"));
            } else {
                invokeMc.returnValue(result);
            }

            MethodCreator methodMetadataMc = classCreator
                    .getMethodCreator(MethodDescriptor.ofMethod(implClassName, "methodMetadata",
                            ToolInvoker.MethodMetadata.class));
            ResultHandle nameToParamPositionHandle = methodMetadataMc.newInstance(HASHMAP_CTOR);
            for (var entry : nameToParamPosition.entrySet()) {
                methodMetadataMc.invokeInterfaceMethod(MAP_PUT, nameToParamPositionHandle,
                        methodMetadataMc.load(entry.getKey()),
                        methodMetadataMc.load(entry.getValue()));
            }

            ResultHandle resultHandle = methodMetadataMc.newInstance(METHOD_METADATA_CTOR,
                    methodMetadataMc.load(toolReturnsVoid),
                    nameToParamPositionHandle,
                    memoryIdParamPosition != null ? methodMetadataMc.load(Integer.valueOf(memoryIdParamPosition))
                            : methodMetadataMc.loadNull());
            methodMetadataMc.returnValue(resultHandle);
        }
        return implClassName;
    }

    private String generateArgumentMapper(MethodInfo methodInfo, ClassOutput classOutput,
            String methodSignature) {
        String implClassName = methodInfo.declaringClass().name() + "$$QuarkusToolArgumentMapper$" + methodInfo.name() + "_"
                + HashUtil.sha1(methodSignature);
        try (ClassCreator classCreator = ClassCreator.builder()
                .classOutput(classOutput)
                .className(implClassName)
                .interfaces(Mappable.class)
                .build()) {

            List<FieldDescriptor> fieldDescriptors = new ArrayList<>();
            for (MethodParameterInfo parameter : methodInfo.parameters()) {
                FieldDescriptor fieldDescriptor = FieldDescriptor.of(implClassName, parameter.name(),
                        parameter.type().name().toString());
                fieldDescriptors.add(fieldDescriptor);
                classCreator.getFieldCreator(fieldDescriptor).setModifiers(Modifier.PUBLIC);
            }

            MethodCreator mc = classCreator
                    .getMethodCreator(MethodDescriptor.ofMethod(implClassName, "obtainFieldValuesMap", Map.class));
            ResultHandle mapHandle = mc.newInstance(MethodDescriptor.ofConstructor(HashMap.class));
            for (FieldDescriptor field : fieldDescriptors) {
                ResultHandle fieldValue = mc.readInstanceField(field, mc.getThis());
                mc.invokeInterfaceMethod(MAP_PUT, mapHandle, mc.load(field.getName()), fieldValue);
            }
            mc.returnValue(mapHandle);
        }
        return implClassName;
    }

    private Iterable<JsonSchemaProperty> toJsonSchemaProperties(MethodParameterInfo parameter, IndexView index) {
        Type type = parameter.type();
        AnnotationInstance pInstance = parameter.annotation(P);

        JsonSchemaProperty description = pInstance == null ? null : description(pInstance.value().asString());

        return toJsonSchemaProperties(type, index, description);
    }

    private Iterable<JsonSchemaProperty> toJsonSchemaProperties(Type type, IndexView index, JsonSchemaProperty description) {
        DotName typeName = type.name();

        if (type.kind() == Type.Kind.WILDCARD_TYPE) {
            Type boundType = type.asWildcardType().extendsBound();
            if (boundType == null) {
                boundType = type.asWildcardType().superBound();
            }
            if (boundType != null) {
                return toJsonSchemaProperties(boundType, index, description);
            } else {
                throw new IllegalArgumentException("Unsupported wildcard type with no bounds: " + type);
            }
        }
        if (DotNames.STRING.equals(typeName) || DotNames.CHARACTER.equals(typeName)
                || DotNames.PRIMITIVE_CHAR.equals(typeName)) {
            return removeNulls(STRING, description);
        }

        if (DotNames.BOOLEAN.equals(typeName) || DotNames.PRIMITIVE_BOOLEAN.equals(typeName)) {
            return removeNulls(BOOLEAN, description);
        }

        if (DotNames.BYTE.equals(typeName) || DotNames.PRIMITIVE_BYTE.equals(typeName)
                || DotNames.SHORT.equals(typeName) || DotNames.PRIMITIVE_SHORT.equals(typeName)
                || DotNames.INTEGER.equals(typeName) || DotNames.PRIMITIVE_INT.equals(typeName)
                || DotNames.LONG.equals(typeName) || DotNames.PRIMITIVE_LONG.equals(typeName)
                || DotNames.BIG_INTEGER.equals(typeName)) {
            return removeNulls(INTEGER, description);
        }

        // TODO put constraints on min and max?
        if (DotNames.FLOAT.equals(typeName) || DotNames.PRIMITIVE_FLOAT.equals(typeName)
                || DotNames.DOUBLE.equals(typeName) || DotNames.PRIMITIVE_DOUBLE.equals(typeName)
                || DotNames.BIG_DECIMAL.equals(typeName)) {
            return removeNulls(NUMBER, description);
        }
        if (LOCAL_DATE_TIME.equals(typeName) || OFFSET_DATE_TIME.equals(typeName)) {
            return removeNulls(JsonSchemaProperty.from("type", "string"), JsonSchemaProperty.from("format", "date-time"),
                    description);
        }

        if (DATE.equals(typeName) || LOCAL_DATE.equals(typeName)) {
            return removeNulls(JsonSchemaProperty.from("type", "string"), JsonSchemaProperty.from("format", "date"),
                    description);
        }
        // TODO something else?
        if (type.kind() == Type.Kind.ARRAY || DotNames.LIST.equals(typeName) || DotNames.SET.equals(typeName)) {
            ParameterizedType parameterizedType = type.kind() == Type.Kind.PARAMETERIZED_TYPE ? type.asParameterizedType()
                    : null;

            Type elementType = parameterizedType != null ? parameterizedType.arguments().get(0)
                    : type.asArrayType().component();

            Iterable<JsonSchemaProperty> elementProperties = toJsonSchemaProperties(elementType, index, null);

            JsonSchemaProperty itemsSchema;
            if (isComplexType(elementType)) {
                Map<String, Object> fieldDescription = new HashMap<>();

                for (JsonSchemaProperty fieldProperty : elementProperties) {
                    fieldDescription.put(fieldProperty.key(), fieldProperty.value());
                }
                itemsSchema = JsonSchemaProperty.from("items", fieldDescription);
            } else {
                itemsSchema = JsonSchemaProperty.items(elementProperties.iterator().next());
            }

            return removeNulls(ARRAY, itemsSchema, description);
        }

        if (isEnum(type, index)) {
            return removeNulls(STRING, enums(enumConstants(type)), description);
        }

        if (type.kind() == Type.Kind.CLASS) {
            Map<String, Object> properties = new HashMap<>();
            ClassInfo classInfo = index.getClassByName(type.name());

            List<String> required = new ArrayList<>();

            if (classInfo != null) {
                for (FieldInfo field : classInfo.fields()) {
                    String fieldName = field.name();
                    Type fieldType = field.type();

                    boolean isOptional = isJavaOptionalType(fieldType);
                    if (isOptional) {
                        fieldType = unwrapOptionalType(fieldType);
                    }

                    Iterable<JsonSchemaProperty> fieldSchema = toJsonSchemaProperties(fieldType, index, null);
                    Map<String, Object> fieldDescription = new HashMap<>();

                    for (JsonSchemaProperty fieldProperty : fieldSchema) {
                        fieldDescription.put(fieldProperty.key(), fieldProperty.value());
                    }

                    if (field.hasAnnotation(Description.class)) {
                        AnnotationInstance descriptionAnnotation = field.annotation(Description.class);
                        if (descriptionAnnotation != null && descriptionAnnotation.value() != null) {
                            String[] descriptionValue = descriptionAnnotation.value().asStringArray();
                            fieldDescription.put("description", String.join(",", descriptionValue));
                        }
                    }
                    if (!isOptional) {
                        required.add(fieldName);
                    }

                    properties.put(fieldName, fieldDescription);
                }
            }

            JsonSchemaProperty objectSchema = JsonSchemaProperty.from("properties", properties);
            return removeNulls(OBJECT, objectSchema, JsonSchemaProperty.from("required", required), description);
        }

        throw new IllegalArgumentException("Unsupported type: " + type);
    }

    private boolean isJavaOptionalType(Type type) {
        DotName typeName = type.name();
        return typeName.equals(DotName.createSimple("java.util.Optional"))
                || typeName.equals(DotName.createSimple("java.util.OptionalInt"))
                || typeName.equals(DotName.createSimple("java.util.OptionalLong"))
                || typeName.equals(DotName.createSimple("java.util.OptionalDouble"));
    }

    private Type unwrapOptionalType(Type optionalType) {
        if (optionalType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType parameterizedType = optionalType.asParameterizedType();
            return parameterizedType.arguments().get(0);
        }
        return optionalType;
    }

    private boolean isComplexType(Type type) {
        return type.kind() == Type.Kind.CLASS || type.kind() == Type.Kind.PARAMETERIZED_TYPE;
    }

    private boolean isOptionalField(FieldInfo field, IndexView index) {
        Type fieldType = field.type();
        DotName fieldTypeName = fieldType.name();

        if (OPTIONAL.equals(fieldTypeName) || OPTIONAL_INT.equals(fieldTypeName) || OPTIONAL_LONG.equals(fieldTypeName)
                || OPTIONAL_DOUBLE.equals(fieldTypeName)) {
            return true;
        }

        return false;

    }

    private Iterable<JsonSchemaProperty> removeNulls(JsonSchemaProperty... properties) {
        return stream(properties)
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private boolean isEnum(Type returnType, IndexView index) {
        if (returnType.kind() != Type.Kind.CLASS) {
            return false;
        }
        ClassInfo maybeEnum = index.getClassByName(returnType.name());
        return maybeEnum != null && maybeEnum.isEnum();
    }

    private static Object[] enumConstants(Type type) {
        return JandexUtil.load(type, Thread.currentThread().getContextClassLoader()).getEnumConstants();
    }

    /**
     * Simply removes the {@code private} modifier from tool methods
     */
    private static class RemovePrivateFromMethodsVisitor implements
            BiFunction<String, ClassVisitor, ClassVisitor> {

        private final List<MethodInfo> privateMethods;

        private RemovePrivateFromMethodsVisitor(List<MethodInfo> privateMethods) {
            this.privateMethods = privateMethods;
        }

        @Override
        public ClassVisitor apply(String className, ClassVisitor classVisitor) {
            ClassTransformer transformer = new ClassTransformer(className);
            for (MethodInfo method : privateMethods) {
                transformer.modifyMethod(MethodDescriptor.of(method)).removeModifiers(Opcodes.ACC_PRIVATE);
            }
            return transformer.applyTo(classVisitor);
        }
    }
}
