package io.quarkiverse.langchain4j.runtime;

/**
 * @deprecated Use {@link dev.langchain4j.service.tool.ToolCallsLimitExceededException}.
 *             This subclass remains only for source/binary compatibility; delegated upstream
 *             paths may throw the upstream exception directly.
 */
@Deprecated
public class ToolCallsLimitExceededException extends dev.langchain4j.service.tool.ToolCallsLimitExceededException {

    public ToolCallsLimitExceededException(int limit, int attempted) {
        super(limit, attempted);
    }
}
