package io.quarkiverse.langchain4j.runtime.tool;

import java.util.HashMap;
import java.util.Map;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;

final class DefaultToolExecutionRequestContext implements ToolExecutionRequestContext {
    private final ToolExecutionRequest request;
    private final InvocationContext invocationContext;
    private final Map<Object, Object> attributes = new HashMap<>();

    DefaultToolExecutionRequestContext(Builder builder) {
        this.request = builder.request;
        this.invocationContext = builder.invocationContext;
        this.attributes.putAll(builder.attributes);
    }

    /**
     * Retrieves the {@link ToolExecutionRequest} associated with this context.
     *
     * @return the tool execution request encapsulated by this context.
     */
    @Override
    public ToolExecutionRequest request() {
        return request;
    }

    /**
     * Retrieves the {@link InvocationContext} associated with this tool execution request context.
     *
     * @return the invocation context encapsulated by this tool execution request context.
     */
    @Override
    public InvocationContext invocationContext() {
        return invocationContext;
    }

    /**
     * Retrieves an unmodifiable view of the attributes associated with this context.
     * The returned map is immutable, ensuring that the attributes cannot be modified externally.
     *
     * @return an unmodifiable map containing the attributes of this context.
     */
    @Override
    public Map<Object, Object> attributes() {
        return Map.copyOf(this.attributes);
    }
}
