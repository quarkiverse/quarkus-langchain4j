package io.quarkiverse.langchain4j.compaction.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.data.message.ChatMessage;

/**
 * In-memory, per-conversation state used by compaction: message timestamps, strategy caches (summaries, embeddings)
 * and the last view handed out (needed to reconcile writes, see {@link CompactingChatMemory#set(Iterable)}).
 * <p>
 * Each compaction profile has its own instance, so two AI services using the same memory id with different profiles do
 * not share strategy caches.
 * <p>
 * The state is a cache: losing it (restart, another node) only means summaries are recomputed and message ages are
 * unknown.
 */
public class ConversationStates {

    static final class State {
        /**
         * Timestamps aligned with the stored history; {@code null} entries are unknown.
         */
        final List<Instant> timestamps = new ArrayList<>();

        final Map<String, Map<String, Object>> strategyState = new HashMap<>();

        List<ChatMessage> lastView;

        void alignTimestamps(int size) {
            while (timestamps.size() < size) {
                timestamps.add(0, null);
            }
            while (timestamps.size() > size) {
                timestamps.remove(0);
            }
        }

        Map<String, Object> state(String owner) {
            return strategyState.computeIfAbsent(owner, k -> new ConcurrentHashMap<>());
        }
    }

    private final Map<Object, State> states = new ConcurrentHashMap<>();

    State get(Object memoryId) {
        return states.computeIfAbsent(memoryId, k -> new State());
    }

    void remove(Object memoryId) {
        states.remove(memoryId);
    }
}
