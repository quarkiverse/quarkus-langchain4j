package io.quarkiverse.langchain4j.runtime.tool;

import java.util.Map;

public interface ToolInvoker {

    MethodMetadata methodMetadata();

    Object invoke(Object tool, Object[] params) throws Exception;

    class MethodMetadata {

        private final boolean returnsVoid;
        private final Map<String, Integer> nameToParamPosition;
        private final Integer memoryIdParamPosition;
        private final Integer invocationParamsParamPosition;

        public MethodMetadata(boolean returnsVoid, Map<String, Integer> nameToParamPosition,
                Integer memoryIdParamPosition, Integer invocationParamsParamPosition) {
            this.returnsVoid = returnsVoid;
            this.nameToParamPosition = nameToParamPosition;
            this.memoryIdParamPosition = memoryIdParamPosition;
            this.invocationParamsParamPosition = invocationParamsParamPosition;
        }

        public boolean isReturnsVoid() {
            return returnsVoid;
        }

        public Map<String, Integer> getNameToParamPosition() {
            return nameToParamPosition;
        }

        public Integer getMemoryIdParamPosition() {
            return memoryIdParamPosition;
        }

        public Integer getInvocationParamsParamPosition() {
            return invocationParamsParamPosition;
        }
    }
}
