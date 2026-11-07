package io.quarkiverse.langchain4j.runtime.tool;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;
import io.quarkiverse.langchain4j.guardrails.ToolMetadata;
import io.quarkiverse.langchain4j.runtime.tool.guardrails.ToolInputGuardrailsLiteral;
import io.quarkiverse.langchain4j.runtime.tool.guardrails.ToolOutputGuardrailsLiteral;

public record ToolMethodCreateInfo(String methodName,
        String invokerClassName,
        ToolSpecification toolSpecification,
        String argumentMapperClassName,
        ExecutionModel executionModel,
        ReturnBehavior returnBehavior,
        ToolInputGuardrailsLiteral inputGuardrails,
        ToolOutputGuardrailsLiteral outputGuardrails) {

    public enum ExecutionModel {
        BLOCKING,
        NON_BLOCKING,
        VIRTUAL_THREAD
    }

    /**
     * Creates ToolMetadata from the tool specification.
     * The tool instance is not available at this level and will be null.
     *
     * @return the tool metadata
     */
    public ToolMetadata getToolMetadata() {
        return new ToolMetadata(toolSpecification, null);
    }

    /**
     * Gets the input guardrails annotation literal for this tool method.
     *
     * @return the input guardrails literal, or null if none configured
     */
    public ToolInputGuardrailsLiteral getInputGuardrails() {
        return inputGuardrails;
    }

    /**
     * Gets the output guardrails annotation literal for this tool method.
     *
     * @return the output guardrails literal, or null if none configured
     */
    public ToolOutputGuardrailsLiteral getOutputGuardrails() {
        return outputGuardrails;
    }
}
