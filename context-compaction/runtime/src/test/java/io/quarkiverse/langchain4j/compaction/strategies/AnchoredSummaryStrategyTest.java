package io.quarkiverse.langchain4j.compaction.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.Fixtures;

class AnchoredSummaryStrategyTest {

    @Test
    void keepsTheAnchorThenTheSummaryThenTheLastTurns() {
        List<ChatMessage> history = Fixtures.conversation(8);
        Fixtures.RecordingSummarizer summarizer = new Fixtures.RecordingSummarizer();
        List<ChatMessage> view = new AnchoredSummaryStrategy(new AnchoredSummaryStrategy.Options(2, 2, 100),
                summarizer).compact(history, Fixtures.context(10_000));

        List<Conversation.Turn> turns = Conversation.parse(history).turns();
        List<ChatMessage> expected = new ArrayList<>();
        expected.add(Fixtures.SYSTEM);
        expected.addAll(turns.get(0).messages());
        expected.addAll(turns.get(1).messages());
        int summaryIndex = expected.size();
        expected.add(view.get(summaryIndex));
        expected.addAll(turns.get(6).messages());
        expected.addAll(turns.get(7).messages());
        assertThat(view).isEqualTo(expected);
        assertThat(CompactionMessages.isSummary(view.get(summaryIndex))).isTrue();
        // only the middle turns (2 to 5) are summarized
        assertThat(summarizer.calls).containsExactly(turns.subList(2, 6).stream().mapToInt(t -> t.messages().size()).sum());
    }

    @Test
    void keepsAnchorAndRecentTurnsWhenSummarizationFails() {
        List<ChatMessage> history = Fixtures.conversation(8);
        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> view = new AnchoredSummaryStrategy(new AnchoredSummaryStrategy.Options(1, 2, 100),
                (previous, messages, maxWords) -> {
                    throw new IllegalStateException("model unavailable");
                }).compact(history, context);

        assertThat(view.get(1)).isEqualTo(history.get(1));
        assertThat(view.get(view.size() - 1)).isEqualTo(history.get(history.size() - 1));
        assertThat(view.stream().filter(CompactionMessages::isSummary)).isEmpty();
        assertThat(context.notes).anyMatch(n -> n.contains("dropped 5 turns without summary"));
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }

    @Test
    void leavesShortConversationsUntouched() {
        List<ChatMessage> history = Fixtures.conversation(3);
        Fixtures.RecordingSummarizer summarizer = new Fixtures.RecordingSummarizer();
        assertThat(new AnchoredSummaryStrategy(new AnchoredSummaryStrategy.Options(1, 2, 100), summarizer)
                .compact(history, Fixtures.context(10_000))).isEqualTo(history);
        assertThat(summarizer.calls).isEmpty();
    }
}
