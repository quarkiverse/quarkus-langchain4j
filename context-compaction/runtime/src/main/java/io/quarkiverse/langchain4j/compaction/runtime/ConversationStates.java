package io.quarkiverse.langchain4j.compaction.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import dev.langchain4j.data.message.ChatMessage;

/**
 * In-memory, per-conversation state used by compaction: message timestamps, strategy caches (summaries, embeddings)
 * and the last view handed out (needed to reconcile writes, see {@link CompactingChatMemory#set(Iterable)}).
 * <p>
 * Each compaction profile has its own instance, so two AI services using the same memory id with different profiles do
 * not share strategy caches.
 * <p>
 * The state is a cache: losing it (restart, another node, eviction) only means summaries are recomputed and message
 * ages are unknown. To keep memory bounded, the state of a conversation is evicted when it has not been used for
 * {@code idleTimeout}, and the least recently used states are evicted when there are more than
 * {@code maxConversations}. A state is never evicted while a call is using it.
 */
public class ConversationStates {

    /**
     * Default maximum number of conversations whose state is kept, per profile.
     */
    public static final int DEFAULT_MAX_CONVERSATIONS = 10_000;

    /**
     * Default idle time after which the state of a conversation is evicted.
     */
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofHours(4);

    static final class State {
        /**
         * Timestamps aligned with the stored history; {@code null} entries are unknown.
         */
        final List<Instant> timestamps = new ArrayList<>();

        final Map<String, Map<String, Object>> strategyState = new HashMap<>();

        List<ChatMessage> lastView;

        /**
         * When messages not stored yet (buffered by an AI service invocation) were first seen, keyed by identity.
         * Cleared when the history is written.
         */
        final Map<ChatMessage, Instant> pending = new IdentityHashMap<>();

        // guarded by the ConversationStates instance
        private int users;
        private Instant lastAccess;

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

        /**
         * Forgets everything, for example when the conversation is cleared.
         */
        void reset() {
            timestamps.clear();
            strategyState.clear();
            pending.clear();
            lastView = null;
        }
    }

    private final int maxConversations;
    private final Duration idleTimeout;
    private final Clock clock;
    private final Consumer<Object> onEvict;

    /**
     * Access-ordered: the least recently used conversation comes first. Guarded by {@code this}.
     */
    private final LinkedHashMap<Object, State> states = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * Uses the default limits and the system clock, and releases nothing on eviction.
     */
    public ConversationStates() {
        this(DEFAULT_MAX_CONVERSATIONS, DEFAULT_IDLE_TIMEOUT, Clock.systemUTC(), id -> {
        });
    }

    /**
     * @param maxConversations the maximum number of conversations whose state is kept
     * @param idleTimeout the idle time after which the state of a conversation is evicted
     * @param clock the clock used to measure idle time
     * @param onEvict called, outside any lock, with the memory id of each evicted conversation, to release the
     *        resources attached to it (inspector snapshot, archived tool outputs...)
     */
    public ConversationStates(int maxConversations, Duration idleTimeout, Clock clock, Consumer<Object> onEvict) {
        if (maxConversations <= 0) {
            throw new IllegalArgumentException("maxConversations must be positive");
        }
        if (idleTimeout.isNegative() || idleTimeout.isZero()) {
            throw new IllegalArgumentException("idleTimeout must be positive");
        }
        this.maxConversations = maxConversations;
        this.idleTimeout = idleTimeout;
        this.clock = clock;
        this.onEvict = onEvict;
    }

    /**
     * Runs {@code action} with the state of the conversation, holding the conversation lock. The state cannot be
     * evicted while the action runs.
     */
    <T> T with(Object memoryId, Function<State, T> action) {
        State state = acquire(memoryId);
        try {
            synchronized (state) {
                return action.apply(state);
            }
        } finally {
            release(state);
        }
    }

    void run(Object memoryId, Consumer<State> action) {
        with(memoryId, state -> {
            action.accept(state);
            return null;
        });
    }

    /**
     * @return the number of conversations whose state is kept
     */
    public synchronized int size() {
        return states.size();
    }

    private State acquire(Object memoryId) {
        synchronized (this) {
            State state = states.computeIfAbsent(memoryId, k -> new State());
            state.users++;
            state.lastAccess = clock.instant();
            return state;
        }
    }

    private void release(State state) {
        List<Object> evicted = new ArrayList<>();
        synchronized (this) {
            state.users--;
            state.lastAccess = clock.instant();
            Instant expiry = state.lastAccess.minus(idleTimeout);
            Iterator<Map.Entry<Object, State>> it = states.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Object, State> entry = it.next();
                State candidate = entry.getValue();
                boolean tooMany = states.size() > maxConversations;
                boolean idle = candidate.lastAccess.isBefore(expiry);
                if (!tooMany && !idle) {
                    // access order: the following conversations were used more recently
                    break;
                }
                if (candidate.users == 0) {
                    it.remove();
                    evicted.add(entry.getKey());
                }
            }
        }
        for (Object id : evicted) {
            onEvict.accept(id);
        }
    }
}
