package io.quarkiverse.langchain4j.guardrails;

import java.util.Collections;
import java.util.Map;

import dev.langchain4j.invocation.InvocationContext;

/**
 * Context information available during tool invocation.
 * <p>
 * This record provides contextual information about the tool invocation that can be used
 * by guardrails for validation decisions. It includes the memory ID (which can correlate
 * to a user session or conversation) and custom parameters that can carry additional context
 * such as user information, security principals, or request metadata.
 * </p>
 *
 * <p>
 * Example usage in a guardrail:
 * </p>
 *
 * <pre>
 * {@code
 * @Override
 * public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
 *     ToolInvocationContext context = request.invocationContext();
 *     if (context != null) {
 *         Object memoryId = context.getMemoryId();
 *         Object userId = context.getParameter("user");
 *
 *         // Use context for authorization or rate limiting
 *         if (!isAuthorized(userId, request.toolName())) {
 *             return ToolInputGuardrailResult.failure("Unauthorized");
 *         }
 *     }
 *     return ToolInputGuardrailResult.success();
 * }
 * }
 * </pre>
 *
 * <p>
 * Custom parameters can be populated by integrating with Quarkus security,
 * CDI request context, or other contextual sources during tool execution setup.
 * </p>
 *
 * @param context the invocation context
 * @see ToolInputGuardrailRequest
 * @see ToolOutputGuardrailRequest
 */
public record ToolInvocationContext(
        InvocationContext context) {

    public ToolInvocationContext {
        if (context == null) {
            throw new IllegalArgumentException("context cannot be null");
        }
    }

    /**
     * Gets the underlying LangChain4j invocation context.
     * <p>
     * Advanced use cases can access the full context to retrieve additional
     * metadata not exposed by convenience methods, such as custom invocation
     * parameters or model-specific context.
     * </p>
     *
     * @return the invocation context, never null
     */
    @Override
    public InvocationContext context() {
        return context;
    }

    /**
     * Gets a parameter value by key.
     * <p>
     * Common parameter keys might include:
     * </p>
     * <ul>
     * <li>"user" - User ID or principal</li>
     * <li>"session" - Session ID</li>
     * <li>"tenant" - Tenant ID for multi-tenant applications</li>
     * <li>"requestId" - Request correlation ID</li>
     * </ul>
     *
     * @param key the parameter key
     * @return the parameter value, or null if not present
     */
    public Object parameter(String key) {
        if (context.invocationParameters() == null) {
            return null;
        }
        return context.invocationParameters().get(key);
    }

    public Object memoryId() {
        return context.chatMemoryId();
    }

    public Map<String, Object> parameters() {
        if (context.invocationParameters() == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(context().invocationParameters().asMap());
    }

    public boolean hasParameter(String key) {
        if (context.invocationParameters() == null) {
            return false;
        }
        return context.invocationParameters().containsKey(key);
    }
}
