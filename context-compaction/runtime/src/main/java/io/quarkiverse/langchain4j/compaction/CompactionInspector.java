package io.quarkiverse.langchain4j.compaction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Keeps, for each conversation, the last full history and the last view sent to the model, plus the most recent
 * compaction reports. Meant for inspection endpoints, and the Dev UI; it holds references to the messages, so it
 * keeps at most {@value #MAX_REPORTS} reports.
 */
@Singleton
public class CompactionInspector {

    static final int MAX_REPORTS = 200;

    /**
     * @param history the full history
     * @param view the view sent to the model (equal to the history when no compaction was needed)
     * @param report the last compaction report, {@code null} if the conversation was never compacted
     */
    public record Snapshot(List<ChatMessage> history, List<ChatMessage> view, CompactionReport report) {
    }

    private final Map<Object, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Deque<CompactionReport> reports = new ArrayDeque<>();

    public void record(Object memoryId, List<ChatMessage> history, List<ChatMessage> view, CompactionReport report) {
        Snapshot previous = snapshots.get(memoryId);
        CompactionReport last = report != null ? report : previous == null ? null : previous.report();
        snapshots.put(memoryId, new Snapshot(List.copyOf(history), List.copyOf(view), last));
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
        return Optional.ofNullable(snapshots.get(memoryId));
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
        snapshots.remove(memoryId);
    }
}
