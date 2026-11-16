package io.quarkiverse.langchain4j.sample.compaction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import io.quarkiverse.langchain4j.compaction.CompactionReport;

/**
 * Tracks the tokens saved by compaction, per conversation. The extension fires a {@link CompactionReport} event each
 * time it compacts a conversation, that is, on every model call once the history exceeds the budget.
 */
@ApplicationScoped
public class TokenGain {

    /**
     * @param last the last compaction of the conversation
     * @param calls the number of compactions since the conversation was replayed (one per model call)
     * @param tokensWithout the tokens the model calls would have received without compaction
     * @param tokensWith the tokens they actually received
     */
    public record Stats(CompactionReport last, int calls, long tokensWithout, long tokensWith) {
    }

    private final Map<Object, Stats> stats = new ConcurrentHashMap<>();

    void onCompaction(@Observes CompactionReport report) {
        stats.merge(report.memoryId(), new Stats(report, 1, report.tokensBefore(), report.tokensAfter()),
                (old, now) -> new Stats(report, old.calls() + 1, old.tokensWithout() + report.tokensBefore(),
                        old.tokensWith() + report.tokensAfter()));
    }

    public Stats get(String conversation) {
        return stats.get(conversation);
    }

    /**
     * Keeps the last report, but restarts the counters (the replay itself does not call the model).
     */
    public void resetCounters(String conversation) {
        stats.computeIfPresent(conversation, (id, s) -> new Stats(s.last(), 0, 0, 0));
    }
}
