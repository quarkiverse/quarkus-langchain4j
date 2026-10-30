package io.quarkiverse.langchain4j.runtime.tool;

import dev.langchain4j.service.tool.ToolExecutionResult;

final class DefaultToolExecutionResponseContext implements ToolExecutionResponseContext {
    private final ToolExecutionRequestContext requestContext;
    private final ToolExecutionResult result;

    DefaultToolExecutionResponseContext(Builder builder) {
        this.requestContext = builder.requestContext;
        this.result = builder.result;
    }

    /**
     * Retrieves the context of the tool execution request, which provides details
     * about the request and invocation.
     */
    @Override
    public ToolExecutionRequestContext requestContext() {
        return requestContext;
    }

    /**
     * Retrieves the result of the tool execution, encapsulating details about the
     * execution outcome such as the response or any errors encountered.
     */
    @Override
    public ToolExecutionResult result() {
        return result;
    }
}
