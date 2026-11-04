package io.quarkiverse.langchain4j.compaction;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * What a compaction did. Fired as a CDI event each time a conversation is compacted, so applications can export
 * metrics or audit records.
 *
 * @param memoryId the conversation
 * @param profile the compaction profile of the AI service ({@code default} when not set, see {@link CompactionProfile})
 * @param strategy the identifier of the strategy that ran
 * @param timestamp when the compaction ran
 * @param messagesBefore number of messages in the full history
 * @param messagesAfter number of messages in the compacted view
 * @param tokensBefore tokens of the full history
 * @param tokensAfter tokens of the compacted view
 * @param budget the budget in effect
 * @param duration time spent compacting (including model calls made by summarizing strategies)
 * @param notes human-readable notes from the strategies
 * @param fallback {@code true} if the strategy failed or broke an invariant and the fallback was used
 */
public record CompactionReport(
        Object memoryId,
        String profile,
        String strategy,
        Instant timestamp,
        int messagesBefore,
        int messagesAfter,
        int tokensBefore,
        int tokensAfter,
        TokenBudget budget,
        Duration duration,
        List<String> notes,
        boolean fallback) {

    public int tokensSaved() {
        return tokensBefore - tokensAfter;
    }
}
