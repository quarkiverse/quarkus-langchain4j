package io.quarkiverse.langchain4j.compaction.strategies;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.Summarizer;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionConfig;
import io.smallrye.common.annotation.Identifier;

/**
 * Replaces everything but the last turns with a single summary message. The summary is rolled forward
 * incrementally: when more turns age out, they are folded into the existing summary instead of re-summarizing the
 * whole history.
 * <p>
 * If summarization fails, the old turns are dropped and the failure is noted in the report.
 */
@Singleton
@Identifier(Strategies.ROLLING_SUMMARY)
public class RollingSummaryStrategy implements CompactionStrategy {

    public record Options(int keepLastTurns, int maxWords) {
    }

    private final Options options;
    private final SummaryEngine engine;

    @Inject
    public RollingSummaryStrategy(CompactionConfig config, Summarizer summarizer) {
        this(config.defaultProfile(), summarizer);
    }

    public RollingSummaryStrategy(CompactionConfig.Profile profile, Summarizer summarizer) {
        this(new Options(profile.summary().keepLastTurns(), profile.summary().maxWords()), summarizer);
    }

    public RollingSummaryStrategy(Options options, Summarizer summarizer) {
        this.options = options;
        this.engine = new SummaryEngine(summarizer, options.maxWords());
    }

    @Override
    public List<ChatMessage> compact(Conversation conversation, CompactionContext context) {
        List<Conversation.Turn> turns = conversation.turns();
        if (turns.size() <= options.keepLastTurns()) {
            return conversation.toMessages();
        }
        int split = turns.size() - options.keepLastTurns();
        List<Conversation.Turn> old = turns.subList(0, split);
        List<Conversation.Turn> recent = turns.subList(split, turns.size());

        Optional<String> summary = engine.summarize(Strategies.ROLLING_SUMMARY, old, context);
        List<ChatMessage> result = new ArrayList<>(conversation.system());
        summary.ifPresent(s -> result.add(CompactionMessages.summary(s)));
        recent.forEach(t -> result.addAll(t.messages()));
        if (summary.isEmpty()) {
            context.note(Strategies.ROLLING_SUMMARY, "dropped " + old.size() + " turns without summary");
        }
        return result;
    }

    @Override
    public String description() {
        return "Summarizes all but the last " + options.keepLastTurns() + " turns into one rolling summary";
    }
}
