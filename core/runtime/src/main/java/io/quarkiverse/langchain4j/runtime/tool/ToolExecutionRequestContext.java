package io.quarkiverse.langchain4j.runtime.tool;

import java.util.HashMap;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;

/**
 * Represents the context for executing a tool within a system. This class encapsulates the request
 * and invocation context related to a specific tool execution.
 *
 * ToolExecutionRequestContext provides a structured approach for handling tool execution information
 * through immutable instances created using a builder.
 */
public sealed interface ToolExecutionRequestContext permits DefaultToolExecutionRequestContext {
    /**
     * Creates a new instance of the {@code Builder} for constructing
     * an immutable {@code ToolExecutionRequestContext}.
     *
     * @return a new {@code Builder} instance for constructing a {@code ToolExecutionRequestContext}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Retrieves the {@link ToolExecutionRequest} associated with this context.
     *
     * @return the tool execution request encapsulated by this context.
     */
    ToolExecutionRequest request();

    /**
     * Retrieves the {@link InvocationContext} associated with this tool execution request context.
     *
     * @return the invocation context encapsulated by this tool execution request context.
     */
    InvocationContext invocationContext();

    /**
     * Retrieves an unmodifiable view of the attributes associated with this context.
     * The returned map is immutable, ensuring that the attributes cannot be modified externally.
     *
     * @return an unmodifiable map containing the attributes of this context.
     */
    Map<Object, Object> attributes();

    /**
     * Creates a {@code Builder} instance initialized with the current state of this
     * {@code ToolExecutionRequestContext}. This allows for modifications to the existing
     * immutable context by creating a modified copy.
     *
     * @return a {@code Builder} pre-populated with the values from this {@code ToolExecutionRequestContext}.
     */
    default Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * A builder class for constructing instances of {@code ToolExecutionRequestContext}.
     * <p>
     * The {@code Builder} class enables a step-by-step construction of a {@code ToolExecutionRequestContext},
     * allowing the customization of the required parameters such as the {@code ToolExecutionRequest} and
     * {@code InvocationContext}.
     * <p>
     * Instances of this builder are obtained through the static {@code builder()} method of
     * {@code ToolExecutionRequestContext}, or the {@code toBuilder()} method on an existing
     * {@code ToolExecutionRequestContext} object for modifications.
     */
    class Builder {
        ToolExecutionRequest request;
        InvocationContext invocationContext;
        Map<Object, Object> attributes = new HashMap<>();

        private Builder() {
        }

        private Builder(ToolExecutionRequestContext requestContext) {
            if (requestContext != null) {
                this.request = requestContext.request();
                this.invocationContext = requestContext.invocationContext();
                this.attributes.putAll(requestContext.attributes());
            }
        }

        /**
         * Sets the {@code ToolExecutionRequest} for the builder.
         * <p>
         * This method allows specifying the tool execution request that will be used
         * when building the {@code ToolExecutionRequestContext}.
         *
         * @param request the {@code ToolExecutionRequest} object containing the details
         *        of the tool execution to be configured in the context
         * @return the {@code Builder} instance to allow method chaining
         */
        public Builder request(ToolExecutionRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Sets the {@code InvocationContext} for the builder.
         * <p>
         * This method allows specifying the invocation context that will be used
         * when building the {@code ToolExecutionRequestContext}.
         *
         * @param invocationContext the {@code InvocationContext} object containing context information
         *        for the tool execution request
         * @return the {@code Builder} instance to allow method chaining
         */
        public Builder invocationContext(InvocationContext invocationContext) {
            this.invocationContext = invocationContext;
            return this;
        }

        /**
         * Sets the attributes for the builder.
         * <p>
         * This method allows specifying a map of attributes to configure in the builder.
         * If the provided map is non-null, its entries are added to the builder's attribute map.
         * Any existing attributes in the builder are cleared before setting the new attributes.
         *
         * @param attributes a map containing key-value pairs representing the attributes to be set;
         *        if {@code null}, the builder's attributes will simply be cleared
         * @return the {@code Builder} instance to allow method chaining
         */
        public Builder attributes(Map<Object, Object> attributes) {
            this.attributes.clear();

            if (attributes != null) {
                this.attributes.putAll(attributes);
            }

            return this;
        }

        /**
         * Adds an attribute to the builder's attribute map.
         * <p>
         * This method allows specifying a key-value pair to be added as an attribute
         * to the builder. If the key is non-null, the key-value pair is added to the
         * builder's attribute map.
         *
         * @param key the key of the attribute to be added; must not be null
         * @param value the value of the attribute to be added
         * @return the {@code Builder} instance to allow method chaining
         */
        public Builder attribute(Object key, Object value) {
            if (key != null) {
                this.attributes.put(key, value);
            }

            return this;
        }

        /**
         * Builds and returns an immutable instance of {@code ToolExecutionRequestContext}.
         * <p>
         * This method completes the construction process of a {@code ToolExecutionRequestContext},
         * encapsulating the state configured in this {@code Builder}.
         *
         * @return a new {@code ToolExecutionRequestContext} instance with the parameters set in the builder
         */
        public ToolExecutionRequestContext build() {
            return new DefaultToolExecutionRequestContext(this);
        }
    }
}
