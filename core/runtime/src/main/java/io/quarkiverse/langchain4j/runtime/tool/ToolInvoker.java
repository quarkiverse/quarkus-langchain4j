package io.quarkiverse.langchain4j.runtime.tool;

import java.util.Map;

public interface ToolInvoker {

    MethodMetadata methodMetadata();

    Object invoke(Object tool, Object[] params) throws Exception;

    class MethodMetadata {

        private final boolean returnsVoid;
        private final Map<String, Integer> nameToParamPosition;
        private final Integer memoryIdParamPosition;

        public MethodMetadata(boolean returnsVoid, Map<String, Integer> nameToParamPosition,
                Integer memoryIdParamPosition) {
            this.returnsVoid = returnsVoid;
            this.nameToParamPosition = nameToParamPosition;
            this.memoryIdParamPosition = memoryIdParamPosition;
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
    }
}
