package io.quarkiverse.langchain4j.deployment;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.objectweb.asm.ClassVisitor;

import dev.langchain4j.model.input.structured.StructuredPrompt;
import io.quarkiverse.langchain4j.runtime.StructuredPromptsRecorder;
import io.quarkiverse.langchain4j.runtime.prompt.Mappable;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;

public class PromptProcessor {

    private static final DotName STRUCTURED_PROMPT = DotName.createSimple(StructuredPrompt.class);
    public static final DotName OBJECT = DotName.createSimple(Object.class.getName());

    public static final MethodDescriptor MAP_PUT = MethodDescriptor.ofMethod(Map.class, "put", Object.class, Object.class,
            Object.class);
    public static final MethodDescriptor MAP_PUT_ALL = MethodDescriptor.ofMethod(Map.class, "putAll", void.class, Map.class);

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public void structuredPromptSupport(StructuredPromptsRecorder recorder,
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<BytecodeTransformerBuildItem> transformerProducer) {
        IndexView index = combinedIndexBuildItem.getIndex();

        Collection<AnnotationInstance> instances = index.getAnnotations(STRUCTURED_PROMPT);
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
                    ClassInfo superClassInfo = OBJECT.equals(superName) ? null : index.getClassByName(superName);
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
    }

    /**
     * We can obtain a map of the class values if the template does not try to access values in any nested level
     */
    private static boolean hasNestedParams(String promptTemplateString) {
        return TemplateUtil.parts(promptTemplateString).stream().anyMatch(p -> p.size() > 1);
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
}
