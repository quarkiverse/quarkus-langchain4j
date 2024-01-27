package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;

import io.quarkus.runtime.annotations.RecordableConstructor;

public class AiServiceClassCreateInfo {

    // the key is a methodId generated at build time
    private final Map<String, AiServiceMethodCreateInfo> methodMap;
    private final String implClassName;

    @RecordableConstructor
    public AiServiceClassCreateInfo(Map<String, AiServiceMethodCreateInfo> methodMap, String implClassName) {
        this.methodMap = methodMap;
        this.implClassName = implClassName;
    }

    public Map<String, AiServiceMethodCreateInfo> getMethodMap() {
        return methodMap;
    }

    public String getImplClassName() {
        return implClassName;
    }
}
