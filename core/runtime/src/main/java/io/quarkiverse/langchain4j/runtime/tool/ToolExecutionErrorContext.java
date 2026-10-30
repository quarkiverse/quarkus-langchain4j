package io.quarkiverse.langchain4j.runtime.tool;

import java.util.Map;

/**
 * Represents the response context of a tool execution, encapsulating the
 * associated request context and the resulting execution outcome.
 */
public sealed interface ToolExecutionErrorContext permits DefaultToolExecutionErrorContext {
    /**
     * Creates and returns a new instance of the {@code Builder} class for constructing
     * an instance of {@code ToolExecutionErrorContext}.
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
     * Retrieves the error associated with the tool execution context.
     * This method provides information about any error that occurred during the tool's execution.
     *
     * @return a {@code Throwable} representing the error, or {@code null} if no error occurred.
     */
    Throwable error();

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
     * A builder class for constructing instances of {@code ToolExecutionErrorContext}.
     * This builder allows for step-by-step construction of a {@code ToolExecutionErrorContext} object
     * by providing methods to set its component properties.
     */
    class Builder {
        ToolExecutionRequestContext requestContext;
        Throwable error;

        private Builder() {
        }

        private Builder(ToolExecutionErrorContext context) {
            this.requestContext = context.requestContext();
            this.error = context.error();
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
         * Sets the error associated with the builder.
         *
         * @param error the {@code Throwable} representing the error to be set.
         * @return this {@code Builder} instance for method chaining.
         */
        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        /**
         * Constructs and returns an instance of {@code ToolExecutionErrorContext}
         * using the current state of the builder.
         *
         * @return a new {@code ToolExecutionErrorContext} instance
         *         initialized with the properties set on this builder.
         */
        public ToolExecutionErrorContext build() {
            return new DefaultToolExecutionErrorContext(this);
        }
    }
}
