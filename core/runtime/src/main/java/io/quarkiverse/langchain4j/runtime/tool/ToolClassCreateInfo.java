package io.quarkiverse.langchain4j.runtime.tool;

import java.util.Map;

import io.quarkus.runtime.annotations.RecordableConstructor;

public class ToolClassCreateInfo {

    // the key is a methodId generated at build time
    private final Map<String, ToolMethodCreateInfo> methodMap;

    @RecordableConstructor
    public ToolClassCreateInfo(Map<String, ToolMethodCreateInfo> methodMap) {
        this.methodMap = methodMap;
    }

    public Map<String, ToolMethodCreateInfo> getMethodMap() {
        return methodMap;
    }
}
