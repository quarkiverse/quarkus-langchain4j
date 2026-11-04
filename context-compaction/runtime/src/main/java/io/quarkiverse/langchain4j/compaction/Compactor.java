package io.quarkiverse.langchain4j.compaction;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.compaction.runtime.CompactingChatMemory;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionProfiles;
import io.quarkiverse.langchain4j.compaction.runtime.DefaultCompactionContext;
import io.smallrye.common.annotation.Identifier;

/**
 * Runs compaction strategies on demand, outside of chat memory: to compare strategies, to compact a transcript before
 * handing it to a sub-agent, or to preview what a strategy would do. Each call uses fresh strategy state, so nothing
 * is cached between calls.
 */
@Singleton
public class Compactor {

    /**
     * @param view the compacted messages
     * @param report what the strategy did
     */
    public record Result(List<ChatMessage> view, CompactionReport report) {
    }

    private final Instance<CompactionStrategy> strategies;
    private final CompactionProfiles profiles;

    @Inject
    public Compactor(@Any Instance<CompactionStrategy> strategies, CompactionProfiles profiles) {
        this.strategies = strategies;
        this.profiles = profiles;
    }

    /**
     * @return the identifiers of the available strategies and their descriptions
     */
    public Map<String, String> strategies() {
        Map<String, String> result = new TreeMap<>();
        for (Instance.Handle<CompactionStrategy> handle : strategies.handles()) {
            for (Annotation qualifier : handle.getBean().getQualifiers()) {
                if (qualifier instanceof Identifier identifier) {
                    result.put(identifier.value(), handle.get().description());
                }
            }
        }
        return result;
    }

    /**
     * Compacts with the budget of the default profile.
     */
    public Result compact(String strategyId, List<ChatMessage> messages) {
        return compact(strategyId, messages, profiles.defaultProvider().components().budget());
    }

    public Result compact(String strategyId, List<ChatMessage> messages, TokenBudget budget) {
        Instance<CompactionStrategy> selected = strategies.select(Identifier.Literal.of(strategyId));
        if (!selected.isResolvable()) {
            throw new CompactionException("Unknown compaction strategy '" + strategyId + "'");
        }
        CompactingChatMemory.Components components = profiles.defaultProvider().components();
        TokenCounter counter = components.tokenCounter();
        Map<String, Map<String, Object>> state = new ConcurrentHashMap<>();
        Clock clock = components.clock();
        DefaultCompactionContext context = new DefaultCompactionContext("compactor", budget, counter,
                new IdentityHashMap<>(), owner -> state.computeIfAbsent(owner, k -> new ConcurrentHashMap<>()), clock);
        Instant start = clock.instant();
        List<ChatMessage> view = selected.get().compact(List.copyOf(messages), context);
        CompactionReport report = new CompactionReport("compactor", null, strategyId, start, messages.size(), view.size(),
                counter.count(messages), counter.count(view), budget, Duration.between(start, clock.instant()),
                context.notes(), false);
        return new Result(view, report);
    }
}
