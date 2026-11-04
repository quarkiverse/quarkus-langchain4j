package io.quarkiverse.langchain4j.compaction.strategies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.Summarizer;

/**
 * Incremental summarization shared by the summarizing strategies.
 * <p>
 * The summary of a prefix of turns is cached in the conversation state, together with a fingerprint of the turns it
 * covers. When more turns age out, only the new ones are folded into the existing summary, so each turn is sent to
 * the summarizer once. If the history no longer matches the fingerprint (for example after an edit), the summary is
 * rebuilt from scratch.
 */
final class SummaryEngine {

    private static final Logger LOG = Logger.getLogger(SummaryEngine.class);

    private static final String COUNT = "summary.turns";
    private static final String FINGERPRINT = "summary.fingerprint";
    private static final String SUMMARY = "summary.text";

    private final Summarizer summarizer;
    private final int maxWords;

    SummaryEngine(Summarizer summarizer, int maxWords) {
        this.summarizer = summarizer;
        this.maxWords = maxWords;
    }

    /**
     * @return the summary of the given turns, or empty if summarization failed
     */
    Optional<String> summarize(String owner, List<Conversation.Turn> turns, CompactionContext context) {
        if (turns.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> state = context.state(owner);
        int cachedCount = (Integer) state.getOrDefault(COUNT, 0);
        String cachedSummary = (String) state.get(SUMMARY);
        Object cachedFingerprint = state.get(FINGERPRINT);

        String previous = null;
        int from = 0;
        if (cachedSummary != null && cachedCount > 0 && cachedCount <= turns.size()
                && Integer.valueOf(fingerprint(turns.subList(0, cachedCount))).equals(cachedFingerprint)) {
            if (cachedCount == turns.size()) {
                context.note(owner, "reused cached summary of " + cachedCount + " turns");
                return Optional.of(cachedSummary);
            }
            previous = cachedSummary;
            from = cachedCount;
        }

        List<ChatMessage> toSummarize = new ArrayList<>();
        turns.subList(from, turns.size()).forEach(t -> toSummarize.addAll(t.messages()));
        try {
            String summary = summarizer.summarize(previous, toSummarize, maxWords);
            state.put(COUNT, turns.size());
            state.put(FINGERPRINT, fingerprint(turns));
            state.put(SUMMARY, summary);
            context.note(owner, previous == null
                    ? "summarized " + turns.size() + " turns"
                    : "folded " + (turns.size() - from) + " turns into the summary of " + from + " turns");
            return Optional.of(summary);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Summarization failed for conversation %s", context.memoryId());
            context.note(owner, "summarization failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    private static int fingerprint(List<Conversation.Turn> turns) {
        return turns.stream()
                .flatMap(t -> t.messages().stream())
                .map(CompactionMessages::text)
                .toList()
                .hashCode();
    }
}
