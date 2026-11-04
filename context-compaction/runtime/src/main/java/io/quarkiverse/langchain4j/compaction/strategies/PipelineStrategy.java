package io.quarkiverse.langchain4j.compaction.strategies;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionConfig;
import io.smallrye.common.annotation.Identifier;

/**
 * Applies strategies in order, cheapest first, and stops as soon as the history fits the target.
 * <p>
 * The default pipeline prunes old tool outputs, collapses repeated tool calls, summarizes old turns, and finally
 * applies a token window as a hard guarantee.
 */
@Singleton
@Identifier(Strategies.PIPELINE)
public class PipelineStrategy implements CompactionStrategy {

    private final Map<String, CompactionStrategy> steps;

    @Inject
    public PipelineStrategy(CompactionConfig config, StrategyFactory factory) {
        this(factory.buildPipelineSteps(config.defaultProfile()));
    }

    public PipelineStrategy(Map<String, CompactionStrategy> steps) {
        this.steps = new LinkedHashMap<>(steps);
    }

    @Override
    public List<ChatMessage> compact(Conversation conversation, CompactionContext context) {
        List<ChatMessage> current = conversation.toMessages();
        List<String> applied = new ArrayList<>();
        for (Map.Entry<String, CompactionStrategy> step : steps.entrySet()) {
            if (context.fits(current)) {
                break;
            }
            current = step.getValue().compact(current, context);
            applied.add(step.getKey() + "=" + context.tokens(current));
        }
        context.note(Strategies.PIPELINE, "steps applied (tokens after each): " + applied);
        if (!context.fits(current)) {
            context.note(Strategies.PIPELINE, "still above target after all steps");
        }
        return current;
    }

    public List<String> steps() {
        return List.copyOf(steps.keySet());
    }

    @Override
    public String description() {
        return "Applies " + steps.keySet() + " in order until the history fits";
    }
}
