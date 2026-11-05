package io.quarkiverse.langchain4j.compaction.runtime;

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
import io.quarkiverse.langchain4j.compaction.CompactionException;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Compactor;
import io.quarkiverse.langchain4j.compaction.TokenBudget;
import io.quarkiverse.langchain4j.compaction.TokenCounter;
import io.smallrye.common.annotation.Identifier;

/**
 * The default {@link Compactor}: runs the strategy beans with fresh state on each call.
 */
@Singleton
public class DefaultCompactor implements Compactor {

    private final Instance<CompactionStrategy> strategies;
    private final DefaultCompactionProfiles profiles;

    @Inject
    public DefaultCompactor(@Any Instance<CompactionStrategy> strategies, DefaultCompactionProfiles profiles) {
        this.strategies = strategies;
        this.profiles = profiles;
    }

    @Override
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

    @Override
    public Result compact(String strategyId, List<ChatMessage> messages) {
        return compact(strategyId, messages, profiles.defaultProvider().components().budget());
    }

    @Override
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
        List<ChatMessage> view = CompactionMessages.foldSummaries(
                selected.get().compact(List.copyOf(messages), context));
        CompactionReport report = new CompactionReport("compactor", DefaultCompactionProfiles.DEFAULT, strategyId, start,
                messages.size(), view.size(),
                counter.count(messages), counter.count(view), budget, Duration.between(start, clock.instant()),
                context.notes(), false, context.degraded());
        return new Result(view, report);
    }
}
