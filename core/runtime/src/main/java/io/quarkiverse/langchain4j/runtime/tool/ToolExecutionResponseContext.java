package io.quarkiverse.langchain4j.runtime.tool;

import java.util.Map;

import dev.langchain4j.service.tool.ToolExecutionResult;

/**
 * Represents the response context of a tool execution, encapsulating the
 * associated request context and the resulting execution outcome.
 */
public sealed interface ToolExecutionResponseContext permits DefaultToolExecutionResponseContext {
    /**
     * Creates and returns a new instance of the {@code Builder} class for constructing
     * an instance of {@code ToolExecutionResponseContext}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Retrieves the context of the tool execution request, which provides details
     * about the request and invocation.
     */
    ToolExecutionRequestContext requestContext();

    /**
     * Retrieves the result of the tool execution, encapsulating details about the
     * execution outcome such as the response or any errors encountered.
     */
    ToolExecutionResult result();

    /**
     * Retrieves a map of attributes associated with the current tool execution request context.
     * These attributes provide additional metadata or details relevant to the execution context.
     *
     * @return an unmodifiable map of attributes for the current request context.
     */
    default Map<Object, Object> attributes() {
        return requestContext().attributes();
    }

    /**
     * Creates a new {@code Builder} instance initialized with the current state of this object.
     * This allows for modifying and regenerating the object while preserving the existing values.
     */
    default Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * A builder class for constructing instances of {@code ToolExecutionResponseContext}.
     * This builder allows for step-by-step construction of a {@code ToolExecutionResponseContext} object
     * by providing methods to set its component properties.
     */
    class Builder {
        ToolExecutionRequestContext requestContext;
        ToolExecutionResult result;

        private Builder() {
        }

        private Builder(ToolExecutionResponseContext context) {
            this.requestContext = context.requestContext();
            this.result = context.result();
        }

        /**
         * Sets the {@link DefaultToolExecutionRequestContext} for the builder.
         *
         * @param requestContext the {@code ToolExecutionRequestContext} to be set.
         * @return this {@code Builder} instance for method chaining.
         */
        public Builder requestContext(ToolExecutionRequestContext requestContext) {
            this.requestContext = requestContext;
            return this;
        }

        /**
         * Sets the {@link ToolExecutionResult} for the builder.
         *
         * @param result the {@code ToolExecutionResult} to be assigned to the builder.
         * @return this {@code Builder} instance for method chaining.
         */
        public Builder result(ToolExecutionResult result) {
            this.result = result;
            return this;
        }

        /**
         * Constructs and returns an instance of {@code ToolExecutionResponseContext}
         * using the current state of the builder.
         *
         * @return a new {@code ToolExecutionResponseContext} instance
         *         initialized with the properties set on this builder.
         */
        public ToolExecutionResponseContext build() {
            return new DefaultToolExecutionResponseContext(this);
        }
    }
}
