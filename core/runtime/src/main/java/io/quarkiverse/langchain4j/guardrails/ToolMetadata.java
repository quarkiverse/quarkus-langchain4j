package io.quarkiverse.langchain4j.guardrails;

import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * Metadata about a tool for use in guardrails.
 * <p>
 * This record provides information about the tool being executed, including its specification
 * and the actual tool instance. Guardrails can use this metadata to make informed validation
 * decisions based on tool characteristics.
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
 *     ToolMetadata metadata = request.toolMetadata();
 *     if (metadata != null) {
 *         String toolName = metadata.toolName();
 *         String description = metadata.description();
 *         ToolSpecification spec = metadata.specification();
 *
 *         // Use metadata for validation decisions
 *         if (requiresAdminAccess(toolName)) {
 *             // Check authorization
 *         }
 *     }
 *     return ToolInputGuardrailResult.success();
 * }
 * }
 * </pre>
 *
 * @param specification the tool specification from LangChain4j containing parameter definitions
 * @param toolInstance the actual tool instance (may be null if omitted)
 *
 * @see ToolSpecification
 * @see ToolInputGuardrailRequest
 * @see ToolOutputGuardrailRequest
 */
public record ToolMetadata(
        ToolSpecification specification,
        Object toolInstance) {

    public ToolMetadata {
        if (specification == null) {
            throw new IllegalArgumentException("specification cannot be null");
        }
    }

    /**
     * Convenience method to get the tool name from the specification.
     *
     * @return the tool name
     */
    public String toolName() {
        return specification.name();
    }

    /**
     * Convenience method to get the tool description from the specification.
     *
     * @return the tool description, or null if not specified
     */
    public String description() {
        return specification.description();
    }
}
