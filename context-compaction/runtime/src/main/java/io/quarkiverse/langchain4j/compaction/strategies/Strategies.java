package io.quarkiverse.langchain4j.compaction.strategies;

/**
 * Identifiers of the built-in strategies, as used in configuration and with
 * {@link io.smallrye.common.annotation.Identifier}.
 */
public final class Strategies {

    public static final String MESSAGE_WINDOW = "message-window";
    public static final String TOKEN_WINDOW = "token-window";
    public static final String TOOL_OUTPUT_PRUNING = "tool-output-pruning";
    public static final String DUPLICATE_COLLAPSE = "duplicate-collapse";
    public static final String ROLLING_SUMMARY = "rolling-summary";
    public static final String ANCHORED_SUMMARY = "anchored-summary";
    public static final String TIME_DECAY = "time-decay";
    public static final String RELEVANCE = "relevance";
    public static final String PIPELINE = "pipeline";

    private Strategies() {
    }
}
