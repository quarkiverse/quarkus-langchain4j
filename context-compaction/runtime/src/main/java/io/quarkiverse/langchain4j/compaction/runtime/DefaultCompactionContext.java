package io.quarkiverse.langchain4j.compaction.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.TokenBudget;
import io.quarkiverse.langchain4j.compaction.TokenCounter;

/**
 * The {@link CompactionContext} used by the application.
 */
public class DefaultCompactionContext implements CompactionContext {

    private final Object memoryId;
    private final TokenBudget budget;
    private final TokenCounter tokenCounter;
    private final Map<ChatMessage, Instant> timestamps;
    private final Function<String, Map<String, Object>> state;
    private final Clock clock;
    private final List<String> notes = new ArrayList<>();

    /**
     * @param timestamps message timestamps, keyed by identity
     * @param state provides the per-strategy state map
     */
    public DefaultCompactionContext(Object memoryId, TokenBudget budget, TokenCounter tokenCounter,
            IdentityHashMap<ChatMessage, Instant> timestamps, Function<String, Map<String, Object>> state, Clock clock) {
        this.memoryId = memoryId;
        this.budget = budget;
        this.tokenCounter = tokenCounter;
        this.timestamps = timestamps;
        this.state = state;
        this.clock = clock;
    }

    @Override
    public Object memoryId() {
        return memoryId;
    }

    @Override
    public TokenBudget budget() {
        return budget;
    }

    @Override
    public TokenCounter tokenCounter() {
        return tokenCounter;
    }

    @Override
    public Optional<Instant> timestamp(ChatMessage message) {
        return Optional.ofNullable(timestamps.get(message));
    }

    @Override
    public Instant now() {
        return clock.instant();
    }

    @Override
    public Map<String, Object> state(String owner) {
        return state.apply(owner);
    }

    @Override
    public void note(String owner, String note) {
        notes.add(owner + ": " + note);
    }

    public List<String> notes() {
        return List.copyOf(notes);
    }
}
