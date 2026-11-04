package io.quarkiverse.langchain4j.agentic.deployment;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkiverse.langchain4j.agentic.runtime.AgentClassSupport;
import io.quarkus.deployment.util.AsmUtil;
import io.quarkus.gizmo.Gizmo;

/**
 * Implements the {@code native} methods of a class-based agent: the native flag is removed and the body delegates to
 * {@link AgentClassSupport#invoke} with the class, the instance, the name and descriptor of the method and its
 * arguments, as the Qute extension implements the {@code native} methods of a {@code @CheckedTemplate} class.
 */
class NativeAgentMethodEnhancer implements BiFunction<String, ClassVisitor, ClassVisitor> {

    private static final String SUPPORT = AgentClassSupport.class.getName().replace('.', '/');
    private static final String INVOKE_DESCRIPTOR = "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;"
            + "[Ljava/lang/Object;)Ljava/lang/Object;";

    private final Map<String, MethodInfo> methods = new HashMap<>();

    /**
     * @param nativeMethods the native methods declared by the transformed class
     */
    NativeAgentMethodEnhancer(List<MethodInfo> nativeMethods) {
        for (MethodInfo method : nativeMethods) {
            methods.put(method.name() + method.descriptor(), method);
        }
    }

    @Override
    public ClassVisitor apply(String className, ClassVisitor outputClassVisitor) {
        String internalName = className.replace('.', '/');
        return new ClassVisitor(Gizmo.ASM_API_VERSION, outputClassVisitor) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                MethodInfo method = methods.get(name + descriptor);
                if (method == null) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                MethodVisitor output = super.visitMethod(access & ~Modifier.NATIVE, name, descriptor, signature,
                        exceptions);
                return new NativeMethodVisitor(internalName, method, descriptor, output);
            }
        };
    }

    private static final class NativeMethodVisitor extends MethodVisitor {

        private final String ownerInternalName;
        private final MethodInfo method;
        private final String descriptor;

        NativeMethodVisitor(String ownerInternalName, MethodInfo method, String descriptor, MethodVisitor output) {
            super(Gizmo.ASM_API_VERSION, output);
            this.ownerInternalName = ownerInternalName;
            this.method = method;
            this.descriptor = descriptor;
        }

        @Override
        public void visitEnd() {
            visitCode();
            // AgentClassSupport.invoke(Owner.class, this, "name", "descriptor", new Object[] { args... })
            visitLdcInsn(org.objectweb.asm.Type.getObjectType(ownerInternalName));
            visitVarInsn(Opcodes.ALOAD, 0);
            visitLdcInsn(method.name());
            visitLdcInsn(descriptor);
            List<Type> parameterTypes = method.parameterTypes();
            visitLdcInsn(parameterTypes.size());
            visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            int slot = 1;
            for (int i = 0; i < parameterTypes.size(); i++) {
                Type parameterType = parameterTypes.get(i);
                visitInsn(Opcodes.DUP);
                visitLdcInsn(i);
                visitVarInsn(AsmUtil.getLoadOpcode(parameterType), slot);
                AsmUtil.boxIfRequired(this, parameterType);
                visitInsn(Opcodes.AASTORE);
                slot += AsmUtil.getParameterSize(parameterType);
            }
            visitMethodInsn(Opcodes.INVOKESTATIC, SUPPORT, "invoke", INVOKE_DESCRIPTOR, false);

            Type returnType = method.returnType();
            if (returnType.kind() == Type.Kind.VOID) {
                visitInsn(Opcodes.POP);
                visitInsn(Opcodes.RETURN);
            } else if (returnType.kind() == Type.Kind.PRIMITIVE) {
                // checks the cast to the wrapper type too
                AsmUtil.unboxIfRequired(this, returnType);
                visitInsn(AsmUtil.getReturnInstruction(returnType));
            } else {
                visitTypeInsn(Opcodes.CHECKCAST, org.objectweb.asm.Type.getType(returnTypeDescriptor()).getInternalName());
                visitInsn(Opcodes.ARETURN);
            }
            visitMaxs(0, 0);
            super.visitEnd();
        }

        private String returnTypeDescriptor() {
            return descriptor.substring(descriptor.lastIndexOf(')') + 1);
        }
    }
}
