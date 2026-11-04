package io.quarkiverse.langchain4j.compaction.strategies;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.Summarizer;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionConfig;
import io.smallrye.common.annotation.Identifier;

/**
 * Compresses turns more aggressively as they age:
 * <ul>
 * <li>recent turns (younger than {@code recent}) are kept verbatim;</li>
 * <li>aging turns have their long messages and tool outputs truncated;</li>
 * <li>stale turns (older than {@code stale}) are folded into a summary.</li>
 * </ul>
 * Turns whose age is unknown (for example, history loaded from a store after a restart) are treated as recent. The
 * last turn is always kept verbatim.
 */
@Singleton
@Identifier(Strategies.TIME_DECAY)
public class TimeDecayStrategy implements CompactionStrategy {

    public record Options(Duration recent, Duration stale, int truncateChars, int maxWords) {
    }

    private final Options options;
    private final SummaryEngine engine;

    @Inject
    public TimeDecayStrategy(CompactionConfig config, Summarizer summarizer) {
        this(config.defaultProfile(), summarizer);
    }

    public TimeDecayStrategy(CompactionConfig.Profile profile, Summarizer summarizer) {
        this(new Options(profile.timeDecay().recent(), profile.timeDecay().stale(),
                profile.timeDecay().truncateChars(), profile.summary().maxWords()), summarizer);
    }

    public TimeDecayStrategy(Options options, Summarizer summarizer) {
        this.options = options;
        this.engine = summarizer == null ? null : new SummaryEngine(summarizer, options.maxWords());
    }

    @Override
    public List<ChatMessage> compact(Conversation conversation, CompactionContext context) {
        List<Conversation.Turn> turns = conversation.turns();
        Instant now = context.now();

        List<Conversation.Turn> stale = new ArrayList<>();
        List<ChatMessage> tail = new ArrayList<>();
        int truncated = 0;
        for (int i = 0; i < turns.size(); i++) {
            Conversation.Turn turn = turns.get(i);
            boolean last = i == turns.size() - 1;
            Optional<Duration> age = context.timestamp(turn.first()).map(t -> Duration.between(t, now));
            if (!last && age.isPresent() && age.get().compareTo(options.stale()) > 0 && tail.isEmpty()) {
                stale.add(turn);
            } else if (!last && age.isPresent() && age.get().compareTo(options.recent()) > 0) {
                for (ChatMessage message : turn.messages()) {
                    ChatMessage shortened = truncate(message);
                    if (shortened != message) {
                        truncated++;
                    }
                    tail.add(shortened);
                }
            } else {
                tail.addAll(turn.messages());
            }
        }

        List<ChatMessage> result = new ArrayList<>(conversation.system());
        if (!stale.isEmpty()) {
            Optional<String> summary = engine == null ? Optional.empty()
                    : engine.summarize(Strategies.TIME_DECAY, stale, context);
            summary.ifPresentOrElse(s -> result.add(CompactionMessages.summary(s)),
                    () -> context.note(Strategies.TIME_DECAY, "dropped " + stale.size() + " stale turns"));
        }
        result.addAll(tail);
        if (truncated > 0) {
            context.note(Strategies.TIME_DECAY, "truncated " + truncated + " messages of aging turns");
        }
        if (turns.stream().allMatch(t -> context.timestamp(t.first()).isEmpty())) {
            context.note(Strategies.TIME_DECAY, "no message timestamps known, nothing to decay");
        }
        return result;
    }

    private ChatMessage truncate(ChatMessage message) {
        int max = options.truncateChars();
        if (message instanceof ToolExecutionResultMessage r && r.text() != null && r.text().length() > max) {
            return CompactionMessages.replaceToolResult(r, shorten(r.text(), max), CompactionMessages.KIND_TRUNCATED);
        } else if (message instanceof AiMessage ai && ai.text() != null && ai.text().length() > max) {
            return CompactionMessages.replaceText(ai, shorten(ai.text(), max), CompactionMessages.KIND_TRUNCATED);
        } else if (message instanceof UserMessage u && u.hasSingleText() && u.singleText().length() > max
                && !CompactionMessages.isDerived(u)) {
            return CompactionMessages.replaceText(u, shorten(u.singleText(), max), CompactionMessages.KIND_TRUNCATED);
        } else {
            return message;
        }
    }

    private static String shorten(String text, int max) {
        return text.substring(0, max) + " [... " + (text.length() - max) + " chars removed by the compaction strategy]";
    }

    @Override
    public String description() {
        return "Keeps turns younger than " + options.recent() + ", truncates aging ones, summarizes turns older than "
                + options.stale();
    }
}
