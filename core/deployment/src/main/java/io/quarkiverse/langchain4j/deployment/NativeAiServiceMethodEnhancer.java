package io.quarkiverse.langchain4j.deployment;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import org.jboss.jandex.MethodInfo;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkus.gizmo.Gizmo;

/**
 * Gives a body to the {@code native} methods of an AI service declared as a class: the native flag is removed and the
 * body throws, telling that the service must be injected. The bean of such a service is the generated subclass of the
 * class, which overrides these methods with the AI service implementation, so the body only runs on an instance the
 * application created itself. Without it the class would carry native methods without an implementation, which the
 * JVM accepts but a native image does not.
 */
class NativeAiServiceMethodEnhancer implements BiFunction<String, ClassVisitor, ClassVisitor> {

    private static final String ILLEGAL_STATE_EXCEPTION = "java/lang/IllegalStateException";

    private final String serviceClassName;
    private final Set<String> methods = new HashSet<>();

    /**
     * @param serviceClassName the name of the AI service class
     * @param nativeMethods the native methods declared by the transformed class, the service class or a superclass
     */
    NativeAiServiceMethodEnhancer(String serviceClassName, List<MethodInfo> nativeMethods) {
        this.serviceClassName = serviceClassName;
        for (MethodInfo method : nativeMethods) {
            methods.add(method.name() + method.descriptor());
        }
    }

    @Override
    public ClassVisitor apply(String className, ClassVisitor outputClassVisitor) {
        return new ClassVisitor(Gizmo.ASM_API_VERSION, outputClassVisitor) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                if (!methods.contains(name + descriptor)) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                MethodVisitor output = super.visitMethod(access & ~Modifier.NATIVE, name, descriptor, signature,
                        exceptions);
                return new ThrowingMethodVisitor(output, "The AI service " + serviceClassName
                        + " must be injected, not instantiated: its method " + name
                        + " is implemented by the bean the extension generates");
            }
        };
    }

    private static final class ThrowingMethodVisitor extends MethodVisitor {

        private final String message;

        ThrowingMethodVisitor(MethodVisitor output, String message) {
            super(Gizmo.ASM_API_VERSION, output);
            this.message = message;
        }

        @Override
        public void visitEnd() {
            visitCode();
            visitTypeInsn(Opcodes.NEW, ILLEGAL_STATE_EXCEPTION);
            visitInsn(Opcodes.DUP);
            visitLdcInsn(message);
            visitMethodInsn(Opcodes.INVOKESPECIAL, ILLEGAL_STATE_EXCEPTION, "<init>", "(Ljava/lang/String;)V", false);
            visitInsn(Opcodes.ATHROW);
            visitMaxs(0, 0);
            super.visitEnd();
        }
    }
}
