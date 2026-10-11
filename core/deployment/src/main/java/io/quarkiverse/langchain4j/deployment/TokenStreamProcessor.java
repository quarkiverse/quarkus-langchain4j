package io.quarkiverse.langchain4j.deployment;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import io.quarkiverse.langchain4j.runtime.aiservice.TokenStreamResponseDispatcher;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;

class TokenStreamProcessor {

    private static final String HANDLER = "dev/langchain4j/service/AiServiceStreamingResponseHandler";
    private static final String RESPONSE = "Ldev/langchain4j/model/chat/response/ChatResponse;";

    @BuildStep
    BytecodeTransformerBuildItem dispatchTokenStreamResponses() {
        // Keep upstream tool-loop and guardrail behavior intact. This handler is package-private and has
        // no executor hook for onCompleteResponse, so add dispatch at its entry point, including later rounds.
        return new BytecodeTransformerBuildItem(HANDLER.replace('/', '.'), (name, visitor) -> new ClassVisitor(
                Opcodes.ASM9, visitor) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("onCompleteResponse") || !descriptor.equals("(" + RESPONSE + ")V")) {
                    return method;
                }
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitFieldInsn(Opcodes.GETFIELD, HANDLER, "methodKey", "Ljava/lang/Object;");
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                TokenStreamResponseDispatcher.class.getName().replace('.', '/'), "dispatch",
                                "(Ldev/langchain4j/model/chat/response/StreamingChatResponseHandler;" + RESPONSE
                                        + "Ljava/lang/Object;)Z",
                                false);
                        Label proceed = new Label();
                        super.visitJumpInsn(Opcodes.IFEQ, proceed);
                        super.visitInsn(Opcodes.RETURN);
                        super.visitLabel(proceed);
                        super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                };
            }
        });
    }
}
