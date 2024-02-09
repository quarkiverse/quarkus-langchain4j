package io.quarkiverse.langchain4j.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import io.quarkiverse.langchain4j.runtime.StructuredPromptsRecorder;
import io.quarkiverse.langchain4j.runtime.prompt.Mappable;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.Gizmo;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

public class PromptProcessor {

    private static final Logger log = Logger.getLogger(AiServicesProcessor.class);

    public static final MethodDescriptor MAP_PUT = MethodDescriptor.ofMethod(Map.class, "put", Object.class, Object.class,
            Object.class);
    public static final MethodDescriptor MAP_PUT_ALL = MethodDescriptor.ofMethod(Map.class, "putAll", void.class, Map.class);

    private static final String STRUCTURED_PROMPT_PROCESSOR_BINARY_NAME = StructuredPromptProcessor.class.getName().replace(".",
            "/");
    private static final String TO_PROMPT = "toPrompt";
    private static final String TO_PROMPT_DESCRIPTOR = "(Ljava/lang/Object;)Ldev/langchain4j/model/input/Prompt;";

    @BuildStep
    public void nativeSupport(BuildProducer<RuntimeInitializedClassBuildItem> producer) {
        producer.produce(new RuntimeInitializedClassBuildItem("dev.langchain4j.rag.content.injector.DefaultContentInjector"));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public void structuredPromptSupport(StructuredPromptsRecorder recorder,
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<BytecodeTransformerBuildItem> transformerProducer) {
        IndexView index = combinedIndexBuildItem.getIndex();

        Collection<AnnotationInstance> instances = index.getAnnotations(Langchain4jDotNames.STRUCTURED_PROMPT);
        for (AnnotationInstance instance : instances) {
            AnnotationTarget target = instance.target();
            if (target.kind() != AnnotationTarget.Kind.CLASS) {
                continue; // should never happen
            }
            String[] parts = instance.value().asStringArray();
            AnnotationValue delimiterValue = instance.value("delimiter");
            String delimiter = delimiterValue != null ? delimiterValue.asString() : "\n";
            String promptTemplateString = String.join(delimiter, parts);
            ClassInfo annotatedClass = target.asClass();
            boolean hasNestedParams = hasNestedParams(promptTemplateString);
            if (!hasNestedParams) {
                ClassInfo current = annotatedClass;
                while (true) {
                    DotName superName = current.superName();
                    ClassInfo superClassInfo = DotNames.OBJECT.equals(superName) ? null : index.getClassByName(superName);
                    transformerProducer.produce(new BytecodeTransformerBuildItem(current.name().toString(),
                            new StructuredPromptAnnotatedTransformer(current, superClassInfo != null,
                                    superName.toString())));
                    if (superClassInfo == null) {
                        break;
                    }
                    current = superClassInfo;
                }

            }
            recorder.add(annotatedClass.name().toString(), promptTemplateString);
        }

        warnForUnsafeUsage(index);
    }

    /**
     * We can obtain a map of the class values if the template does not try to access values in any nested level
     */
    private static boolean hasNestedParams(String promptTemplateString) {
        return TemplateUtil.parts(promptTemplateString).stream().anyMatch(p -> p.size() > 1);
    }

    /**
     * When an object is passed to {@link StructuredPromptProcessor#toPrompt(Object)} the class of the object needs
     * to be annotated with {@link dev.langchain4j.model.input.structured.StructuredPrompt} otherwise an error
     * will be thrown at runtime time.
     */
    private void warnForUnsafeUsage(IndexView index) {
        Set<String> candidates = new HashSet<>();

        for (ClassInfo classInfo : index.getKnownUsers(Langchain4jDotNames.STRUCTURED_PROMPT_PROCESSOR)) {
            String className = classInfo.name().toString();
            if (className.startsWith("io.quarkiverse.langchain4j") || className.startsWith("dev.langchain4j")) { // TODO: this can be made smarter if needed
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
                    analyze(cn, method, candidates);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Reading bytecode of class '" + className + "' failed", e);
            } catch (AnalyzerException e) {
                log.debug("Unable to analyze bytecode of class '" + className + "'", e);
            }
        }

        for (String candidate : candidates) {
            ClassInfo classInfo = index.getClassByName(candidate);
            if (classInfo == null) {
                continue;
            }
            if (!classInfo.hasDeclaredAnnotation(Langchain4jDotNames.STRUCTURED_PROMPT)) {
                log.warn("Class '" + candidate
                        + "' is used in StructuredPromptProcessor but it is not annotated with @StructuredPrompt. This will likely result in an exception being thrown when the prompt is used.");
            }
        }
    }

    private void analyze(ClassNode clazz, MethodNode method, Set<String> candidates) throws AnalyzerException {
        Type currentClass = Type.getObjectType(clazz.name);
        Type currentSuperClass = Type.getObjectType(clazz.superName);
        List<Type> currentInterfaces = clazz.interfaces.stream().map(Type::getObjectType).collect(Collectors.toList());
        boolean isInterface = (clazz.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE;
        Interpreter<BasicValue> interpreter = new SimpleVerifier(Opcodes.ASM9, currentClass, currentSuperClass,
                currentInterfaces, isInterface) {
            @Override
            public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
                if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode method = (MethodInsnNode) insn;
                    if (STRUCTURED_PROMPT_PROCESSOR_BINARY_NAME.equals(method.owner)
                            && TO_PROMPT.equals(method.name)
                            && TO_PROMPT_DESCRIPTOR.equals(method.desc)) {
                        BasicValue basicValue = values.get(0);
                        if (basicValue instanceof UnionValue) {
                            UnionValue unionValue = (UnionValue) basicValue;
                            candidates.addAll(unionValue.union.stream().map(Type::getClassName).collect(Collectors.toSet()));
                        } else {
                            candidates.add(basicValue.getType().getClassName());
                        }
                    }
                }
                return super.naryOperation(insn, values);
            }

            @Override
            public BasicValue newValue(Type type) {
                BasicValue result = super.newValue(type);
                return UnionValue.create(result);
            }

            @Override
            public BasicValue merge(BasicValue value1, BasicValue value2) {
                BasicValue result = super.merge(value1, value2);
                return UnionValue.create(result, value1, value2);
            }
        };
        Analyzer<BasicValue> analyzer = new Analyzer<>(interpreter);
        analyzer.analyze(clazz.name, method);
    }

    /**
     * Simple class transformer that adds the {@link Mappable} interface to the class and implement the method
     * by simply reading all properties of the class itself
     */
    private static class StructuredPromptAnnotatedTransformer implements
            BiFunction<String, ClassVisitor, ClassVisitor> {

        private final ClassInfo annotatedClass;
        private final boolean hasSuperMappable;
        private final String superClassName;

        private StructuredPromptAnnotatedTransformer(ClassInfo annotatedClass, boolean hasSuperMappable,
                String superClassName) {
            this.annotatedClass = annotatedClass;
            this.hasSuperMappable = hasSuperMappable;
            this.superClassName = superClassName;
        }

        @Override
        public ClassVisitor apply(String s, ClassVisitor classVisitor) {
            ClassTransformer transformer = new ClassTransformer(annotatedClass.name().toString());
            transformer.addInterface(Mappable.class);

            MethodCreator mc = transformer.addMethod("obtainFieldValuesMap", Map.class);
            ResultHandle mapHandle = mc.newInstance(MethodDescriptor.ofConstructor(HashMap.class));
            for (FieldInfo field : annotatedClass.fields()) {
                short modifiers = field.flags();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                    continue;
                }
                String name = field.name();
                ResultHandle fieldValue = mc.readInstanceField(field, mc.getThis());
                mc.invokeInterfaceMethod(MAP_PUT, mapHandle, mc.load(name), fieldValue);
            }
            if (hasSuperMappable) {
                ResultHandle mapFromSuper = mc
                        .invokeSpecialMethod(MethodDescriptor.ofMethod(superClassName, "obtainFieldValuesMap",
                                Map.class), mc.getThis());
                mc.invokeInterfaceMethod(MAP_PUT_ALL, mapFromSuper, mapHandle);
                mc.returnValue(mapFromSuper);
            } else {
                mc.returnValue(mapHandle);
            }

            return transformer.applyTo(classVisitor);
        }
    }

    private static class UnionValue extends BasicValue {
        private final Set<Type> union;

        public static BasicValue create(BasicValue value) {
            if (value == null) { // void
                return null;
            }
            if (value.getType() == null) { // uninitialized value
                return new UnionValue(null, Set.of());
            }
            return new UnionValue(value.getType(), Set.of(value.getType()));
        }

        public static BasicValue create(BasicValue lub, BasicValue value1, BasicValue value2) {
            HashSet<Type> union = new HashSet<>();
            union.addAll(((UnionValue) value1).union);
            union.addAll(((UnionValue) value2).union);
            return new UnionValue(lub.getType(), Set.copyOf(union));
        }

        private UnionValue(Type lubType, Set<Type> union) {
            super(lubType);
            this.union = Objects.requireNonNull(union);
        }

        @Override
        public String toString() {
            return super.toString() + " | union of " + union;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof UnionValue))
                return false;
            if (!super.equals(o))
                return false;
            UnionValue that = (UnionValue) o;
            return Objects.equals(union, that.union);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), union);
        }
    }
}
