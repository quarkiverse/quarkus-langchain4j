package io.quarkiverse.langchain4j.compaction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import io.smallrye.common.annotation.Experimental;

/**
 * Keeps the most recent compaction reports and, for the conversations of the profiles that enable snapshots
 * ({@code inspector-snapshots}, on by default in dev and test mode), the last full history and the last view sent to the
 * model. Meant for inspection endpoints and the Dev UI.
 * <p>
 * Everything is bounded: at most {@value #MAX_REPORTS} reports and the snapshots of the {@value #MAX_SNAPSHOTS} most
 * recently active conversations. The snapshot of a conversation is also dropped when the conversation is cleared or
 * its compaction state is evicted.
 */
@Singleton
@Experimental("This feature is experimental and the API is subject to change")
public class CompactionInspector {

    static final int MAX_REPORTS = 200;
    static final int MAX_SNAPSHOTS = 100;

    /**
     * @param history the full history
     * @param view the view sent to the model (equal to the history when no compaction was needed)
     * @param report the last compaction report, {@code null} if the conversation was never compacted
     */
    public record Snapshot(List<ChatMessage> history, List<ChatMessage> view, CompactionReport report) {
    }

    /**
     * Access-ordered, so the least recently active conversation is dropped first. Guarded by itself.
     */
    private final LinkedHashMap<Object, Snapshot> snapshots = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Object, Snapshot> eldest) {
            return size() > MAX_SNAPSHOTS;
        }
    };
    private final Deque<CompactionReport> reports = new ArrayDeque<>();

    /**
     * Records the snapshot of a conversation, and the report if the conversation was compacted.
     */
    public void record(Object memoryId, List<ChatMessage> history, List<ChatMessage> view, CompactionReport report) {
        Snapshot snapshot = new Snapshot(List.copyOf(history), List.copyOf(view), report);
        synchronized (snapshots) {
            Snapshot previous = snapshots.get(memoryId);
            if (report == null && previous != null && previous.report() != null) {
                snapshot = new Snapshot(snapshot.history(), snapshot.view(), previous.report());
            }
            snapshots.put(memoryId, snapshot);
        }
        record(report);
    }

    /**
     * Records a report without a snapshot.
     */
    public void record(CompactionReport report) {
        if (report != null) {
            synchronized (reports) {
                reports.addFirst(report);
                while (reports.size() > MAX_REPORTS) {
                    reports.removeLast();
                }
            }
        }
    }

    public Optional<Snapshot> snapshot(Object memoryId) {
        synchronized (snapshots) {
            return Optional.ofNullable(snapshots.get(memoryId));
        }
    }

    /**
     * @return the most recent reports, newest first
     */
    public List<CompactionReport> reports() {
        synchronized (reports) {
            return new ArrayList<>(reports);
        }
    }

    public void remove(Object memoryId) {
        synchronized (snapshots) {
            snapshots.remove(memoryId);
        }
    }
}
