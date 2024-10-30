package io.quarkiverse.langchain4j.runtime.tool;

import dev.langchain4j.agent.tool.ToolSpecification;

public record ToolMethodCreateInfo(String methodName,
        String invokerClassName,
        ToolSpecification toolSpecification,
        String argumentMapperClassName,
        ExecutionModel executionModel) {

    public enum ExecutionModel {
        BLOCKING,
        NON_BLOCKING,
        VIRTUAL_THREAD
    }

}
