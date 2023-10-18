package io.quarkiverse.langchain4j.runtime.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class ToolMethodCreateInfo {

    private final String methodName;
    private final String invokerClassName;
    private final ToolSpecification toolSpecification;

    private final String argumentMapperClassName;

    @RecordableConstructor
    public ToolMethodCreateInfo(String methodName, String invokerClassName, ToolSpecification toolSpecification,
            String argumentMapperClassName) {
        this.methodName = methodName;
        this.invokerClassName = invokerClassName;
        this.toolSpecification = toolSpecification;
        this.argumentMapperClassName = argumentMapperClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getInvokerClassName() {
        return invokerClassName;
    }

    public ToolSpecification getToolSpecification() {
        return toolSpecification;
    }

    public String getArgumentMapperClassName() {
        return argumentMapperClassName;
    }
}
