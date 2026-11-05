package io.quarkiverse.langchain4j.compaction.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkiverse.langchain4j.compaction.CompactionInspector;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.TokenBudget;
import io.quarkiverse.langchain4j.compaction.TokenCounter;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;
import io.quarkiverse.langchain4j.compaction.strategies.TokenWindowStrategy;
import io.quarkiverse.langchain4j.memory.HistoryViewChatMemory;

/**
 * A {@link ChatMemory} that stores the full history and hands out a compacted view.
 * <p>
 * The {@link ChatMemoryStore} always holds the complete, original history; compaction only shapes what
 * {@link #messages()} returns, so it is auditable and can be changed without losing data. Compaction runs when the
 * history exceeds the trigger of the {@link io.quarkiverse.langchain4j.compaction.TokenBudget}.
 * <p>
 * Quarkus LangChain4j AI services use it as a {@link HistoryViewChatMemory}: they buffer the full {@link #history()}
 * during an invocation, ask for the compacted {@link #view(List)} of that buffer before each request to the model (so
 * the tool calls and results of a long invocation are compacted too), and write the buffer back with
 * {@link #setHistory(List)}.
 * <p>
 * Other callers only see the {@link ChatMemory} contract: {@link #messages()} returns the compacted view, and
 * {@link #set(Iterable)} reconciles: if the incoming list extends the last view handed out, only the new tail is
 * appended to the stored history.
 */
public class CompactingChatMemory implements HistoryViewChatMemory {

    private static final Logger LOG = Logger.getLogger(CompactingChatMemory.class);

    /**
     * Everything the memory needs that is shared across conversations.
     */
    public record Components(
            String profile,
            ChatMemoryStore store,
            String strategyId,
            CompactionStrategy strategy,
            TokenBudget budget,
            TokenCounter tokenCounter,
            boolean enabled,
            boolean verifyInvariants,
            ConversationStates states,
            CompactionInspector inspector,
            ToolOutputArchive archive,
            Consumer<CompactionReport> events,
            Clock clock) {
    }

    private static final CompactionStrategy FALLBACK = new TokenWindowStrategy();

    private final Object id;
    private final Components c;

    public CompactingChatMemory(Object id, Components components) {
        this.id = id;
        this.c = components;
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        ConversationStates.State state = c.states().get(id);
        synchronized (state) {
            List<ChatMessage> history = new ArrayList<>(c.store().getMessages(id));
            state.alignTimestamps(history.size());
            if (message instanceof SystemMessage) {
                int existing = indexOfSystem(history);
                if (existing >= 0) {
                    if (history.get(existing).equals(message)) {
                        return;
                    }
                    history.set(existing, message);
                    state.timestamps.set(existing, now());
                } else {
                    history.add(0, message);
                    state.timestamps.add(0, now());
                }
            } else {
                history.add(message);
                state.timestamps.add(now());
            }
            state.lastView = null;
            c.store().updateMessages(id, history);
        }
    }

    @Override
    public void set(Iterable<ChatMessage> messages) {
        List<ChatMessage> incoming = new ArrayList<>();
        messages.forEach(incoming::add);
        ConversationStates.State state = c.states().get(id);
        synchronized (state) {
            List<ChatMessage> history = new ArrayList<>(c.store().getMessages(id));
            state.alignTimestamps(history.size());
            List<ChatMessage> lastView = state.lastView;
            state.lastView = null;

            Optional<List<ChatMessage>> tail = lastView == null ? Optional.empty() : newTail(lastView, incoming);
            if (tail.isPresent()) {
                Optional<ChatMessage> system = incoming.stream().filter(m -> m instanceof SystemMessage).findFirst();
                if (system.isPresent()) {
                    int existing = indexOfSystem(history);
                    if (existing < 0) {
                        history.add(0, system.get());
                        state.timestamps.add(0, now());
                    } else if (!history.get(existing).equals(system.get())) {
                        history.set(existing, system.get());
                    }
                }
                for (ChatMessage message : tail.get()) {
                    history.add(message);
                    state.timestamps.add(now());
                }
                c.store().updateMessages(id, history);
                return;
            }

            // Not an extension of the last view: the caller rewrote the history, store it as is.
            if (lastView != null && !lastView.equals(history)) {
                LOG.debugf("Conversation %s: history rewritten by the caller, compacted view replaced the history", id);
            }
            List<Instant> timestamps = new ArrayList<>();
            for (ChatMessage message : incoming) {
                int index = history.indexOf(message);
                timestamps.add(index >= 0 ? state.timestamps.get(index) : now());
            }
            state.timestamps.clear();
            state.timestamps.addAll(timestamps);
            c.store().updateMessages(id, incoming);
        }
    }

    @Override
    public List<ChatMessage> messages() {
        ConversationStates.State state = c.states().get(id);
        synchronized (state) {
            List<ChatMessage> history = c.store().getMessages(id);
            List<ChatMessage> view = computeView(history, state);
            state.lastView = List.copyOf(view);
            return new ArrayList<>(view);
        }
    }

    @Override
    public List<ChatMessage> history() {
        ConversationStates.State state = c.states().get(id);
        synchronized (state) {
            return new ArrayList<>(c.store().getMessages(id));
        }
    }

    @Override
    public List<ChatMessage> view(List<ChatMessage> history) {
        ConversationStates.State state = c.states().get(id);
        synchronized (state) {
            return new ArrayList<>(computeView(List.copyOf(history), state));
        }
    }

    @Override
    public void setHistory(List<ChatMessage> history) {
        List<ChatMessage> messages = List.copyOf(history);
        ConversationStates.State state = c.states().get(id);
        synchronized (state) {
            IdentityHashMap<ChatMessage, Instant> known = timestamps(messages, state);
            state.timestamps.clear();
            for (ChatMessage message : messages) {
                state.timestamps.add(known.get(message));
            }
            state.pending.clear();
            state.lastView = null;
            c.store().updateMessages(id, messages);
        }
    }

    /**
     * @param history the full history: the stored one, or the buffer of an AI service invocation (stored messages
     *        followed by messages not stored yet)
     */
    private List<ChatMessage> computeView(List<ChatMessage> history, ConversationStates.State state) {
        int tokens = c.tokenCounter().count(history);
        if (!c.enabled() || !c.budget().exceeded(tokens)) {
            c.inspector().record(id, history, history, null);
            return history;
        }
        return compact(history, tokens, timestamps(history, state), state);
    }

    /**
     * Timestamps of the given messages, keyed by identity. Stored messages get the time they were stored (or
     * {@code null} if unknown); messages not stored yet get the time they were first seen by {@link #view(List)}.
     */
    private IdentityHashMap<ChatMessage, Instant> timestamps(List<ChatMessage> messages,
            ConversationStates.State state) {
        List<ChatMessage> stored = c.store().getMessages(id);
        state.alignTimestamps(stored.size());
        IdentityHashMap<ChatMessage, Instant> known = new IdentityHashMap<>();
        for (int i = 0; i < stored.size(); i++) {
            known.put(stored.get(i), state.timestamps.get(i));
        }
        IdentityHashMap<ChatMessage, Instant> result = new IdentityHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (known.containsKey(message)) {
                result.put(message, known.get(message));
            } else if (i < stored.size() && stored.get(i).equals(message)) {
                // stores that deserialize on each read return equal, but not identical, messages
                result.put(message, state.timestamps.get(i));
            } else {
                result.put(message, state.pending.computeIfAbsent(message, m -> now()));
            }
        }
        return result;
    }

    private List<ChatMessage> compact(List<ChatMessage> history, int tokensBefore,
            IdentityHashMap<ChatMessage, Instant> timestamps, ConversationStates.State state) {
        Instant start = c.clock().instant();
        timestamps.values().removeIf(Objects::isNull);
        DefaultCompactionContext context = new DefaultCompactionContext(id, c.budget(), c.tokenCounter(), timestamps,
                state::state, c.clock());
        String strategyId = c.strategyId();
        boolean fallback = false;
        List<ChatMessage> view;
        try {
            view = c.strategy().compact(List.copyOf(history), context);
            if (c.verifyInvariants()) {
                List<String> violations = CompactionInvariants.violations(history, view);
                if (!violations.isEmpty()) {
                    context.note(strategyId, "invariants violated " + violations + ", using token-window");
                    view = FALLBACK.compact(List.copyOf(history), context);
                    fallback = true;
                }
            }
        } catch (RuntimeException e) {
            LOG.warnf(e, "Compaction strategy '%s' (profile %s) failed for conversation %s, using token-window",
                    strategyId, c.profile(), id);
            context.note(strategyId, "failed: " + e.getMessage() + ", using token-window");
            view = FALLBACK.compact(List.copyOf(history), context);
            fallback = true;
        }
        CompactionReport report = new CompactionReport(id, c.profile(), strategyId, start, history.size(), view.size(),
                tokensBefore, c.tokenCounter().count(view), c.budget(),
                Duration.between(start, c.clock().instant()), context.notes(), fallback);
        c.inspector().record(id, history, view, report);
        if (c.events() != null) {
            c.events().accept(report);
        }
        return view;
    }

    @Override
    public void clear() {
        ConversationStates.State state = c.states().get(id);
        synchronized (state) {
            c.store().deleteMessages(id);
            c.states().remove(id);
            c.inspector().remove(id);
            if (c.archive() != null) {
                c.archive().clear(id);
            }
        }
    }

    /**
     * @return the messages appended to {@code lastView}, ignoring system messages, or empty if {@code incoming} does not
     *         start with {@code lastView}
     */
    static Optional<List<ChatMessage>> newTail(List<ChatMessage> lastView, List<ChatMessage> incoming) {
        List<ChatMessage> view = withoutSystem(lastView);
        List<ChatMessage> in = withoutSystem(incoming);
        if (in.size() < view.size() || !in.subList(0, view.size()).equals(view)) {
            return Optional.empty();
        }
        return Optional.of(in.subList(view.size(), in.size()));
    }

    private static List<ChatMessage> withoutSystem(List<ChatMessage> messages) {
        return messages.stream().filter(m -> !(m instanceof SystemMessage)).toList();
    }

    private static int indexOfSystem(List<ChatMessage> history) {
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i) instanceof SystemMessage) {
                return i;
            }
        }
        return -1;
    }

    private Instant now() {
        return c.clock().instant();
    }
}
