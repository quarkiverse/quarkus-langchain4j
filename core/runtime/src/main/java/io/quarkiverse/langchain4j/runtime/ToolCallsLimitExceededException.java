package io.quarkiverse.langchain4j.runtime;

/**
 * Thrown when an LLM response exceeds the configured
 * maximum number of tool calls per response.
 */
public class ToolCallsLimitExceededException extends RuntimeException {

    private final int limit;
    private final int attempted;

    public ToolCallsLimitExceededException(int limit, int attempted) {
        super(String.format(
                "Exceeded maximum tool calls per response: %d (attempted: %d)",
                limit, attempted));
        this.limit = limit;
        this.attempted = attempted;
    }

    public int getLimit() {
        return limit;
    }

    public int getAttempted() {
        return attempted;
    }
}
