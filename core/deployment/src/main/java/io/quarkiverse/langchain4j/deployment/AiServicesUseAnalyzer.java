package io.quarkiverse.langchain4j.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

import dev.langchain4j.service.AiServices;

class AiServicesUseAnalyzer {

    private static final String AI_SERVICES_BINARY_NAME = AiServices.class.getName().replace(".", "/");

    /**
     * This looks at the bytecode of the method and tries to determine the class used in any AiServices#create call
     */
    static Result analyze(ClassNode clazz, MethodNode method) throws AnalyzerException {
        List<Result.Entry> analysisEntries = new ArrayList<>();

        Type currentClass = Type.getObjectType(clazz.name);
        Type currentSuperClass = Type.getObjectType(clazz.superName);
        List<Type> currentInterfaces = clazz.interfaces.stream().map(Type::getObjectType).collect(Collectors.toList());
        boolean isInterface = (clazz.access & Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE;
        SimpleVerifier simpleVerifier = new SimpleVerifier(Opcodes.ASM9, currentClass, currentSuperClass, currentInterfaces,
                isInterface) {

            /**
             * The point of these variables is to keep track of the metadata used from the invocation of .builder() until
             * .build()
             * Theoretically multiple calls could be interlaced, but it's very uncommon for builders and as this is a best
             * effort
             * approach, we don't deal with such complexity
             */
            private final AtomicReference<String> classNameInBuilder = new AtomicReference<>();
            private final AtomicBoolean chatProviderProviderUsed = new AtomicBoolean();

            @Override
            public BasicValue newOperation(final AbstractInsnNode insn) throws AnalyzerException {
                if (insn.getOpcode() == LDC) {
                    // as BasicValue does not track the parameter loaded, we need to manually do the bookkeeping
                    Object value = ((LdcInsnNode) insn).cst;
                    if (value instanceof Type) {
                        try {
                            String className = ((Type) value).getClassName();
                            return new LdcTrackingBasicValue(
                                    Type.getObjectType("java/lang/Class"), // this is what is used internally by default
                                    className);
                        } catch (Exception ignored) {
                        }
                    }
                }
                return super.newOperation(insn);
            }

            @Override
            public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values)
                    throws AnalyzerException {
                if (insn.getType() == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode method = (MethodInsnNode) insn;
                    if (AI_SERVICES_BINARY_NAME.equals(method.owner)) {
                        if ("builder".equals(method.name) && !values.isEmpty()) {
                            BasicValue basicValue = values.get(0); // the class is always the first parameter
                            if (basicValue instanceof LdcTrackingBasicValue) {
                                String createdClassName = ((LdcTrackingBasicValue) basicValue).getClassName();
                                classNameInBuilder.set(createdClassName);
                            }
                        } else if ("create".equals(method.name) && !values.isEmpty()) {
                            BasicValue basicValue = values.get(0); // the class is always the first parameter
                            if (basicValue instanceof LdcTrackingBasicValue) {
                                String createdClassName = ((LdcTrackingBasicValue) basicValue).getClassName();
                                // in this case we know that a chatMemoryProvider has not been used because AiServices#create does not provide such a setting
                                analysisEntries.add(new Result.Entry(createdClassName, false));
                            }
                        } else if ("chatMemoryProvider".equals(method.name)) {
                            chatProviderProviderUsed.set(true);
                        } else if ("build".equals(method.name)) {
                            String createdClassName = classNameInBuilder.get();
                            if (createdClassName != null) {
                                analysisEntries.add(new Result.Entry(createdClassName, chatProviderProviderUsed.get()));
                            }
                            // clear the trackers
                            classNameInBuilder.set(null);
                            chatProviderProviderUsed.set(false);
                        }
                    }
                }
                return super.naryOperation(insn, values);
            }
        };
        Analyzer<BasicValue> analyzer = new Analyzer<>(simpleVerifier);
        simpleVerifier.setClassLoader(Thread.currentThread().getContextClassLoader());
        analyzer.analyze(clazz.name, method);

        return new Result(analysisEntries);
    }

    static class Result {
        final List<Entry> entries;

        public Result(List<Entry> entries) {
            this.entries = entries;
        }

        static class Entry {
            final String createdClassName;
            final boolean chatMemoryProviderUsed;

            public Entry(String createdClassName, boolean chatMemoryProviderUsed) {
                this.createdClassName = createdClassName;
                this.chatMemoryProviderUsed = chatMemoryProviderUsed;
            }
        }
    }

    private static class LdcTrackingBasicValue extends BasicValue {
        private final String className;

        public LdcTrackingBasicValue(Type type, String className) {
            super(type);
            this.className = className;
        }

        public String getClassName() {
            return className;
        }
    }
}
