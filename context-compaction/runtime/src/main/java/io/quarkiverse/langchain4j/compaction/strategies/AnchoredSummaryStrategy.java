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
 * Like the rolling summary, but the first turns (the anchor, usually the user's original goal and constraints) are
 * kept verbatim. Long-running agents tend to drift when the original instructions only survive inside a summary;
 * anchoring keeps them exact.
 */
@Singleton
@Identifier(Strategies.ANCHORED_SUMMARY)
public class AnchoredSummaryStrategy implements CompactionStrategy {

    public record Options(int anchorTurns, int keepLastTurns, int maxWords) {
    }

    private final Options options;
    private final SummaryEngine engine;

    @Inject
    public AnchoredSummaryStrategy(CompactionConfig config, Summarizer summarizer) {
        this(config.defaultProfile(), summarizer);
    }

    public AnchoredSummaryStrategy(CompactionConfig.Profile profile, Summarizer summarizer) {
        this(new Options(profile.summary().anchorTurns(), profile.summary().keepLastTurns(),
                profile.summary().maxWords()), summarizer);
    }

    public AnchoredSummaryStrategy(Options options, Summarizer summarizer) {
        this.options = options;
        this.engine = new SummaryEngine(summarizer, options.maxWords());
    }

    @Override
    public List<ChatMessage> compact(Conversation conversation, CompactionContext context) {
        List<Conversation.Turn> turns = conversation.turns();
        if (turns.size() <= options.anchorTurns() + options.keepLastTurns()) {
            return conversation.toMessages();
        }
        int split = turns.size() - options.keepLastTurns();
        List<Conversation.Turn> anchor = turns.subList(0, options.anchorTurns());
        List<Conversation.Turn> middle = turns.subList(options.anchorTurns(), split);
        List<Conversation.Turn> recent = turns.subList(split, turns.size());

        Optional<String> summary = engine.summarize(Strategies.ANCHORED_SUMMARY, middle, context);
        List<ChatMessage> result = new ArrayList<>(conversation.system());
        anchor.forEach(t -> result.addAll(t.messages()));
        summary.ifPresent(s -> result.add(CompactionMessages.summary(s)));
        recent.forEach(t -> result.addAll(t.messages()));
        if (summary.isEmpty()) {
            context.note(Strategies.ANCHORED_SUMMARY, "dropped " + middle.size() + " turns without summary");
        }
        return result;
    }

    @Override
    public String description() {
        return "Keeps the first " + options.anchorTurns() + " turn(s) and the last " + options.keepLastTurns()
                + ", summarizes the rest";
    }
}
