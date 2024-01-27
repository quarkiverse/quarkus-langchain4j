package io.quarkiverse.langchain4j.runtime.tool;

import java.util.Map;

public interface ToolInvoker {

    MethodMetadata methodMetadata();

    Object invoke(Object tool, Object[] params) throws Exception;

    class MethodMetadata {

        private final boolean returnsVoid;
        private final Map<String, Integer> nameToParamPosition;

        public MethodMetadata(boolean returnsVoid, Map<String, Integer> nameToParamPosition) {
            this.returnsVoid = returnsVoid;
            this.nameToParamPosition = nameToParamPosition;
        }

        public boolean isReturnsVoid() {
            return returnsVoid;
        }

        public Map<String, Integer> getNameToParamPosition() {
            return nameToParamPosition;
        }

    }
}
