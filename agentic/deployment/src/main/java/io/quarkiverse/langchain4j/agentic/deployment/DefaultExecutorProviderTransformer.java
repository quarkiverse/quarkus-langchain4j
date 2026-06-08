package io.quarkiverse.langchain4j.agentic.deployment;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class DefaultExecutorProviderTransformer extends ClassVisitor {

    private static final String OWNER = "dev/langchain4j/internal/DefaultExecutorProvider";
    private static final String FIELD_NAME = "quarkusOverride";
    private static final String FIELD_DESC = "Ljava/util/concurrent/ExecutorService;";
    private static final int FIELD_ACCESS = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE;

    DefaultExecutorProviderTransformer(ClassVisitor delegate) {
        super(Opcodes.ASM9, delegate);
    }

    @Override
    public void visitEnd() {
        FieldVisitor fv = cv.visitField(FIELD_ACCESS, FIELD_NAME, FIELD_DESC, null, null);
        if (fv != null) {
            fv.visitEnd();
        }

        MethodVisitor mv = cv.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "setDefaultExecutorService",
                "(Ljava/util/concurrent/ExecutorService;)V",
                null, null);
        if (mv != null) {
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.PUTSTATIC, OWNER, FIELD_NAME, FIELD_DESC);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        super.visitEnd();
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        if ("getDefaultExecutorService".equals(name) && "()Ljava/util/concurrent/ExecutorService;".equals(descriptor)) {
            return new GetterPrefixVisitor(mv);
        }
        return mv;
    }

    private static class GetterPrefixVisitor extends MethodVisitor {

        private boolean injected = false;

        GetterPrefixVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            if (!injected) {
                injected = true;
                mv.visitFieldInsn(Opcodes.GETSTATIC, OWNER, FIELD_NAME, FIELD_DESC);
                mv.visitVarInsn(Opcodes.ASTORE, 0);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                var skipLabel = new org.objectweb.asm.Label();
                mv.visitJumpInsn(Opcodes.IFNULL, skipLabel);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitLabel(skipLabel);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(Math.max(maxStack, 1), Math.max(maxLocals, 1));
        }
    }
}
