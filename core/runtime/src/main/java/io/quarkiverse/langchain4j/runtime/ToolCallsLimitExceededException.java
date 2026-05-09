package io.quarkiverse.langchain4j.runtime;

/**
 * Thrown when an LLM response exceeds the configured
 * maximum number of tool calls per response.
 *
 * <p>
 * As of langchain4j 1.14, the canonical exception class is
 * {@link dev.langchain4j.service.tool.ToolCallsLimitExceededException}; this Quarkus-specific class
 * remains a subclass for source compatibility with code that catches it explicitly. The streaming
 * tool-execution path still throws this Quarkus class; the non-streaming path delegates to
 * upstream and may throw the upstream class directly. Catch the upstream class going forward.
 */
public class ToolCallsLimitExceededException extends dev.langchain4j.service.tool.ToolCallsLimitExceededException {

    public ToolCallsLimitExceededException(int limit, int attempted) {
        super(limit, attempted);
    }
}
