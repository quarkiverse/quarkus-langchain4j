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
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
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

    private static final Logger log = Logger.getLogger(AiServicesProcessor.class);

    private static final DotName TOOL = DotName.createSimple(Tool.class);
    private static final DotName P = DotName.createSimple(dev.langchain4j.agent.tool.P.class);
    private static final MethodDescriptor METHOD_METADATA_CTOR = MethodDescriptor
            .ofConstructor(ToolInvoker.MethodMetadata.class, boolean.class, Map.class);
    private static final MethodDescriptor HASHMAP_CTOR = MethodDescriptor.ofConstructor(HashMap.class);
    public static final MethodDescriptor MAP_PUT = MethodDescriptor.ofMethod(Map.class, "put", Object.class, Object.class,
            Object.class);

    @BuildStep
    public void telemetry(Capabilities capabilities, BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {
        var addOpenTelemetrySpan = capabilities.isPresent(Capability.OPENTELEMETRY_TRACER);
        if (addOpenTelemetrySpan) {
            additionalBeanProducer.produce(AdditionalBeanBuildItem.builder().addBeanClass(ToolSpanWrapper.class).build());
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public void handleTools(CombinedIndexBuildItem indexBuildItem,
            ToolsRecorder recorder,
            RecorderContext recorderContext,
            BuildProducer<BytecodeTransformerBuildItem> transformerProducer,
            BuildProducer<GeneratedClassBuildItem> generatedClassProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> validation,
            BuildProducer<ToolsMetadataBuildItem> toolsMetadataProducer) {
        recorderContext.registerSubstitution(ToolSpecification.class, ToolSpecificationObjectSubstitution.Serialized.class,
                ToolSpecificationObjectSubstitution.class);
        recorderContext.registerSubstitution(ToolParameters.class, ToolParametersObjectSubstitution.Serialized.class,
                ToolParametersObjectSubstitution.class);

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
                    if (toolMethod.parametersCount() == 0) {
                        validation.produce(
                                new ValidationPhaseBuildItem.ValidationErrorBuildItem(new IllegalStateException(
                                        "Tool method '" + toolMethod.name() + "' on class '" + className
                                                + "' must have at least one parameter")));
                        validationErrorFound = true;
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

                    AnnotationValue nameValue = instance.value("name");
                    AnnotationValue descriptionValue = instance.value();

                    String toolName = getToolName(nameValue, toolMethod);
                    String toolDescription = getToolDescription(descriptionValue);

                    ToolSpecification.Builder builder = ToolSpecification.builder()
                            .name(toolName)
                            .description(toolDescription);

                    for (MethodParameterInfo parameter : toolMethod.parameters()) {
                        builder.addParameter(parameter.name(), toJsonSchemaProperties(parameter, index));
                    }

                    Map<String, Integer> nameToParamPosition = toolMethod.parameters().stream().collect(
                            Collectors.toMap(MethodParameterInfo::name, i -> Integer.valueOf(i.position())));

                    String methodSignature = createUniqueSignature(toolMethod);

                    String invokerClassName = generateInvoker(toolMethod, classOutput, nameToParamPosition, methodSignature);
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

        toolsMetadataProducer.produce(new ToolsMetadataBuildItem(metadata));
        recorder.setMetadata(metadata);
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
            Map<String, Integer> nameToParamPosition, String methodSignature) {
        String implClassName = methodInfo.declaringClass().name() + "$$QuarkusInvoker$" + methodInfo.name() + "_"
                + HashUtil.sha1(methodSignature);
        try (ClassCreator classCreator = ClassCreator.builder()
                .classOutput(classOutput)
                .className(implClassName)
                .interfaces(ToolInvoker.class)
                .build()) {

            MethodCreator invokeMc = classCreator.getMethodCreator(
                    MethodDescriptor.ofMethod(implClassName, "invoke", Object.class, Object.class, Object[].class));

            ResultHandle[] targetMethodHandles = null;
            if (methodInfo.parametersCount() != 0) {
                List<ResultHandle> argumentHandles = new ArrayList<>(methodInfo.parametersCount());
                for (int i = 0; i < methodInfo.parametersCount(); i++) {
                    argumentHandles.add(invokeMc.readArrayValue(invokeMc.getMethodParam(1), i));
                }
                targetMethodHandles = argumentHandles.toArray(new ResultHandle[0]);
            }
            ResultHandle result = invokeMc.invokeVirtualMethod(MethodDescriptor.of(methodInfo), invokeMc.getMethodParam(0),
                    targetMethodHandles);
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
                    methodMetadataMc.load(toolReturnsVoid), nameToParamPositionHandle);
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
        DotName typeName = parameter.type().name();

        AnnotationInstance pInstance = parameter.annotation(P);
        JsonSchemaProperty description = pInstance == null ? null : description(pInstance.value().asString());

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

        if ((type.kind() == Type.Kind.ARRAY)
                || DotNames.LIST.equals(typeName)
                || DotNames.SET.equals(typeName)) { // TODO something else?
            return removeNulls(ARRAY, description); // TODO provide type of array?
        }

        if (isEnum(type, index)) {
            return removeNulls(STRING, enums(enumConstants(type)), description);
        }

        return removeNulls(OBJECT, description); // TODO provide internals
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
