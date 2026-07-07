package io.quarkiverse.langchain4j.runtime.tool;

final class DefaultToolExecutionErrorContext implements ToolExecutionErrorContext {
    private final ToolExecutionRequestContext requestContext;
    private final Throwable error;

    DefaultToolExecutionErrorContext(Builder builder) {
        this.requestContext = builder.requestContext;
        this.error = builder.error;
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
     * Retrieves the error associated with the tool execution context.
     * This method provides information about any error that occurred during the tool's execution.
     *
     * @return a {@code Throwable} representing the error, or {@code null} if no error occurred.
     */
    @Override
    public Throwable error() {
        return this.error;
    }
}
