package io.quarkiverse.langchain4j.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.REGISTER_DECISION_SERVICE;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.interceptor.InterceptorBinding;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;

import dev.langchain4j.service.IllegalConfigurationException;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkiverse.langchain4j.runtime.decision.DecisionServiceCreateInfo;
import io.quarkiverse.langchain4j.runtime.decision.DecisionServiceMethodImplementationSupport;
import io.quarkiverse.langchain4j.runtime.decision.DecisionServicesRecorder;
import io.quarkiverse.langchain4j.runtime.decision.QuarkusDecisionServiceContext;
import io.quarkiverse.langchain4j.runtime.decision.QuarkusDecisionServiceContextQualifier;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.gizmo.CatchBlockCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.gizmo.TryBlock;

/**
 * Makes CDI beans of the interfaces annotated with {@code @RegisterDecisionService}: each one gets a generated
 * implementation, {@code X$$QuarkusDecisionServiceImpl}, whose methods delegate to the upstream {@code DecisionMethod}
 * through a {@link QuarkusDecisionServiceContext} bean holding the decision model and the thresholds of the service.
 */
public class DecisionServicesProcessor {

    private static final String IMPL_SUFFIX = "$$QuarkusDecisionServiceImpl";
    private static final DotName QUALIFIER = DotName.createSimple(QuarkusDecisionServiceContextQualifier.class);
    private static final DotName APPLICATION_SCOPED = DotName.createSimple(ApplicationScoped.class);
    private static final DotName INTERCEPTOR_BINDING = DotName.createSimple(InterceptorBinding.class);
    private static final MethodDescriptor INVOKE = MethodDescriptor.ofMethod(
            DecisionServiceMethodImplementationSupport.class, "invoke", Object.class,
            QuarkusDecisionServiceContext.class, Method.class, Object[].class);

    @BuildStep
    void findDecisionServices(CombinedIndexBuildItem indexBuildItem,
            BuildProducer<DecisionServiceBuildItem> decisionServiceProducer,
            BuildProducer<RequestDecisionModelBeanBuildItem> requestDecisionModelProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer) {
        IndexView index = indexBuildItem.getIndex();
        Set<String> decisionModelNames = new HashSet<>();
        Set<String> interfaces = new LinkedHashSet<>();
        Set<String> reflectiveTypes = new LinkedHashSet<>();
        for (AnnotationInstance annotation : index.getAnnotations(REGISTER_DECISION_SERVICE)) {
            if (annotation.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }
            ClassInfo iface = annotation.target().asClass();
            if (!iface.isInterface()) {
                throw new IllegalConfigurationException(
                        "@RegisterDecisionService can only be used on interfaces, but '%s' is a class"
                                .formatted(iface.name()));
            }
            List<ClassInfo> hierarchy = new ArrayList<>();
            hierarchy.add(iface);
            hierarchy.addAll(JandexUtil.getAllSuperinterfaces(iface, index));

            List<MethodInfo> methods = abstractMethods(hierarchy);
            if (methods.isEmpty()) {
                throw new IllegalConfigurationException(
                        "Decision Service '%s' has no abstract method to implement".formatted(iface.name()));
            }
            boolean booleanAnswer = false;
            for (MethodInfo method : methods) {
                validateMethod(iface, method, index);
                booleanAnswer |= DecisionTypes.hasBooleanAnswer(DecisionTypes.contentType(method.returnType()), index);
                DecisionTypes.reflectiveTypes(method, index).forEach(type -> reflectiveTypes.add(type.toString()));
            }
            for (ClassInfo type : hierarchy) {
                for (DotName prompt : List.of(LangChain4jDotNames.USER_MESSAGE, LangChain4jDotNames.SYSTEM_MESSAGE)) {
                    if (!type.annotations(prompt).isEmpty()) {
                        throw new IllegalConfigurationException(("Decision Service '%s' must not use @%s: its questions "
                                + "are the ones of @Decide").formatted(iface.name(), prompt.withoutPackagePrefix()));
                    }
                }
                interfaces.add(type.name().toString());
            }

            AnnotationValue modelNameValue = annotation.value("modelName");
            String decisionModelName = modelNameValue == null ? NamedConfigUtil.DEFAULT_NAME : modelNameValue.asString();
            AnnotationValue scopeValue = annotation.value("scope");
            DotName scope = scopeValue == null ? APPLICATION_SCOPED : scopeValue.asClass().name();
            decisionModelNames.add(decisionModelName);
            decisionServiceProducer.produce(
                    new DecisionServiceBuildItem(iface, methods, decisionModelName, scope, booleanAnswer));
        }

        if (interfaces.isEmpty()) {
            return;
        }
        decisionModelNames.forEach(name -> requestDecisionModelProducer.produce(new RequestDecisionModelBeanBuildItem(name)));
        unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(LangChain4jDotNames.THRESHOLD_PROVIDER));
        // the methods of the interfaces are analyzed by reflection when the services are created
        reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(interfaces.toArray(new String[0]))
                .methods().reason(DecisionServicesProcessor.class.getName()).build());
        if (!reflectiveTypes.isEmpty()) {
            reflectiveClassProducer.produce(ReflectiveClassBuildItem.builder(reflectiveTypes.toArray(new String[0]))
                    .constructors().methods().fields().reason(DecisionServicesProcessor.class.getName()).build());
        }
    }

    private static List<MethodInfo> abstractMethods(List<ClassInfo> hierarchy) {
        List<MethodInfo> methods = new ArrayList<>();
        Set<String> signatures = new HashSet<>();
        for (ClassInfo type : hierarchy) {
            for (MethodInfo method : type.methods()) {
                if (Modifier.isStatic(method.flags()) || !Modifier.isAbstract(method.flags())) {
                    continue;
                }
                if (signatures.add(method.name() + method.descriptor())) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    /**
     * Applies the rules of the upstream {@code DecisionMethod} about the return type, the finer ones being checked when
     * the service is created.
     */
    private static void validateMethod(ClassInfo iface, MethodInfo method, IndexView index) {
        Type contentType = DecisionTypes.contentType(method.returnType());
        boolean decide = method.hasDeclaredAnnotation(DecisionTypes.DECIDE);
        if (DecisionTypes.isAnswerType(contentType, index)) {
            if (!decide) {
                throw new IllegalConfigurationException(("Method '%s' of Decision Service '%s' must be annotated with "
                        + "@Decide, which contains the question to answer").formatted(method.name(), iface.name()));
            }
        } else if (DecisionTypes.objectAnswerType(contentType, index) != null) {
            if (decide) {
                throw new IllegalConfigurationException(("Method '%s' of Decision Service '%s' returns %s, which "
                        + "contains several questions: annotate its fields with @Decide instead of the method")
                        .formatted(method.name(), iface.name(), contentType.name().withoutPackagePrefix()));
            }
        } else {
            throw new IllegalConfigurationException(("Method '%s' of Decision Service '%s' has an unsupported return "
                    + "type: %s. Supported types are boolean, YesNoAnswer, an enum, Choice<enum>, Scale<enum>, and "
                    + "objects whose fields annotated with @Decide are of these types, optionally wrapped in "
                    + "DecisionResult<> and/or CompletableFuture<>").formatted(method.name(), iface.name(),
                            method.returnType()));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void createContextBeans(List<DecisionServiceBuildItem> decisionServices, DecisionServicesRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanProducer) {
        for (DecisionServiceBuildItem decisionService : decisionServices) {
            String serviceClassName = decisionService.getServiceInterface().name().toString();
            String decisionModelName = decisionService.getDecisionModelName();
            AnnotationInstance modelQualifier = NamedConfigUtil.isDefault(decisionModelName)
                    ? AnnotationInstance.builder(Default.class).build()
                    : AnnotationInstance.builder(ModelName.class).add("value", decisionModelName).build();
            syntheticBeanProducer.produce(SyntheticBeanBuildItem
                    .configure(QuarkusDecisionServiceContext.class)
                    .unremovable()
                    .forceApplicationClass()
                    .createWith(recorder.createContext(new DecisionServiceCreateInfo(serviceClassName, decisionModelName)))
                    .setRuntimeInit()
                    .addQualifier().annotation(QUALIFIER).addValue("value", serviceClassName).done()
                    .scope(Dependent.class)
                    .addInjectionPoint(ClassType.create(LangChain4jDotNames.DECISION_MODEL), modelQualifier)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(LangChain4jDotNames.THRESHOLD_PROVIDER) }, null))
                    .done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void validateThresholdConfig(List<DecisionServiceBuildItem> decisionServices, DecisionServicesRecorder recorder,
            BeanContainerBuildItem beanContainer) {
        Map<String, Boolean> booleanAnswers = new HashMap<>();
        for (DecisionServiceBuildItem decisionService : decisionServices) {
            // services sharing a simple name share their configuration, which must suit all of them
            booleanAnswers.merge(decisionService.getServiceInterface().simpleName(), decisionService.hasBooleanAnswer(),
                    Boolean::logicalAnd);
        }
        recorder.validateThresholdConfig(booleanAnswers);
    }

    /**
     * Generates the implementation of each Decision Service, like the following:
     *
     * <pre>
     * {
     *     &#64;code
     *     &#64;ApplicationScoped
     *     public class SpamFilter$$QuarkusDecisionServiceImpl implements SpamFilter {
     *         private static Method METHOD_0;
     *         private final QuarkusDecisionServiceContext context;
     *
     *         static {
     *             METHOD_0 = SpamFilter.class.getMethod("isSpam", String.class);
     *         }
     *
     *         @Inject
     *         public SpamFilter$$QuarkusDecisionServiceImpl(
     *                 &#64;QuarkusDecisionServiceContextQualifier("SpamFilter") QuarkusDecisionServiceContext context) {
     *             this.context = context;
     *         }
     *
     *         public boolean isSpam(String message) {
     *             return (Boolean) DecisionServiceMethodImplementationSupport.invoke(context, METHOD_0,
     *                     new Object[] { message });
     *         }
     *     }
     * }
     * </pre>
     */
    @BuildStep
    void generateImplementations(List<DecisionServiceBuildItem> decisionServices, CombinedIndexBuildItem indexBuildItem,
            BuildProducer<GeneratedBeanBuildItem> generatedBeanProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableProducer) {
        IndexView index = indexBuildItem.getIndex();
        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeanProducer);
        for (DecisionServiceBuildItem decisionService : decisionServices) {
            ClassInfo iface = decisionService.getServiceInterface();
            String ifaceName = iface.name().toString();
            String implClassName = ifaceName + IMPL_SUFFIX;
            try (ClassCreator cc = ClassCreator.builder()
                    .classOutput(classOutput)
                    .className(implClassName)
                    .interfaces(ifaceName)
                    .build()) {
                cc.addAnnotation(decisionService.getScope().toString());
                for (AnnotationInstance annotation : iface.declaredAnnotations()) {
                    if (isInterceptorBinding(annotation, index)) {
                        cc.addAnnotation(annotation);
                    }
                }

                FieldDescriptor contextField = cc.getFieldCreator("context", QuarkusDecisionServiceContext.class)
                        .setModifiers(Modifier.PRIVATE | Modifier.FINAL)
                        .getFieldDescriptor();
                try (MethodCreator ctor = cc.getMethodCreator(MethodDescriptor.INIT, "V",
                        QuarkusDecisionServiceContext.class)) {
                    ctor.setModifiers(Modifier.PUBLIC);
                    ctor.addAnnotation(Inject.class);
                    ctor.getParameterAnnotations(0).addAnnotation(QUALIFIER.toString()).add("value", ifaceName);
                    ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(Object.class), ctor.getThis());
                    ctor.writeInstanceField(contextField, ctor.getThis(), ctor.getMethodParam(0));
                    ctor.returnVoid();
                }
                // for the client proxy of normal scoped beans
                try (MethodCreator ctor = cc.getMethodCreator(MethodDescriptor.INIT, "V")) {
                    ctor.setModifiers(Modifier.PUBLIC);
                    ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(Object.class), ctor.getThis());
                    ctor.writeInstanceField(contextField, ctor.getThis(), ctor.loadNull());
                    ctor.returnVoid();
                }

                List<MethodInfo> methods = decisionService.getMethods();
                List<FieldDescriptor> methodFields = new ArrayList<>();
                for (int i = 0; i < methods.size(); i++) {
                    methodFields.add(cc.getFieldCreator("METHOD_" + i, Method.class)
                            .setModifiers(Modifier.PRIVATE | Modifier.STATIC)
                            .getFieldDescriptor());
                }
                generateStaticInitializer(cc, methods, methodFields);
                for (int i = 0; i < methods.size(); i++) {
                    generateMethod(cc, contextField, methodFields.get(i), methods.get(i), index);
                }
            }
            unremovableProducer.produce(UnremovableBeanBuildItem.beanClassNames(implClassName));
        }
    }

    private static void generateStaticInitializer(ClassCreator cc, List<MethodInfo> methods,
            List<FieldDescriptor> methodFields) {
        try (MethodCreator clinit = cc.getMethodCreator(MethodDescriptor.ofMethod(cc.getClassName(), "<clinit>",
                void.class))) {
            clinit.setModifiers(Modifier.STATIC);
            TryBlock tryBlock = clinit.tryBlock();
            for (int i = 0; i < methods.size(); i++) {
                MethodInfo method = methods.get(i);
                ResultHandle parameterTypes = tryBlock.newArray(Class.class, method.parametersCount());
                for (int p = 0; p < method.parametersCount(); p++) {
                    tryBlock.writeArrayValue(parameterTypes, p, tryBlock.loadClass(typeName(method.parameterType(p))));
                }
                ResultHandle reflectiveMethod = tryBlock.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(Class.class, "getMethod", Method.class, String.class, Class[].class),
                        tryBlock.loadClass(method.declaringClass().name().toString()), tryBlock.load(method.name()),
                        parameterTypes);
                tryBlock.writeStaticField(methodFields.get(i), reflectiveMethod);
            }
            CatchBlockCreator catchBlock = tryBlock.addCatch(Exception.class);
            catchBlock.throwException(catchBlock.newInstance(
                    MethodDescriptor.ofConstructor(RuntimeException.class, String.class, Throwable.class),
                    catchBlock.load("Failed to initialize the methods of the Decision Service"),
                    catchBlock.getCaughtException()));
            clinit.returnVoid();
        }
    }

    private static void generateMethod(ClassCreator cc, FieldDescriptor contextField, FieldDescriptor methodField,
            MethodInfo method, IndexView index) {
        String[] parameterTypes = method.parameterTypes().stream().map(DecisionServicesProcessor::typeName)
                .toArray(String[]::new);
        Type returnType = method.returnType();
        try (MethodCreator mc = cc.getMethodCreator(method.name(), typeName(returnType), parameterTypes)) {
            mc.setModifiers(Modifier.PUBLIC);
            for (AnnotationInstance annotation : method.declaredAnnotations()) {
                if (isInterceptorBinding(annotation, index)) {
                    mc.addAnnotation(annotation);
                }
            }
            ResultHandle args = mc.newArray(Object.class, parameterTypes.length);
            for (int i = 0; i < parameterTypes.length; i++) {
                mc.writeArrayValue(args, i, mc.checkCast(mc.getMethodParam(i), Object.class));
            }
            ResultHandle result = mc.invokeStaticMethod(INVOKE, mc.readInstanceField(contextField, mc.getThis()),
                    mc.readStaticField(methodField), args);
            if (returnType.kind() == Type.Kind.PRIMITIVE) {
                String wrapper = PrimitiveType.box(returnType.asPrimitiveType()).name().toString();
                mc.returnValue(mc.smartCast(mc.checkCast(result, wrapper), typeName(returnType)));
            } else {
                mc.returnValue(mc.checkCast(result, typeName(returnType)));
            }
        }
    }

    private static String typeName(Type type) {
        if (type.kind() == Type.Kind.PRIMITIVE) {
            return type.asPrimitiveType().primitive().name().toLowerCase();
        }
        return type.name().toString();
    }

    private static boolean isInterceptorBinding(AnnotationInstance annotation, IndexView index) {
        ClassInfo annotationClass = index.getClassByName(annotation.name());
        if (annotationClass != null) {
            return annotationClass.hasDeclaredAnnotation(INTERCEPTOR_BINDING);
        }
        try {
            return Class.forName(annotation.name().toString(), false, Thread.currentThread().getContextClassLoader())
                    .isAnnotationPresent(InterceptorBinding.class);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
