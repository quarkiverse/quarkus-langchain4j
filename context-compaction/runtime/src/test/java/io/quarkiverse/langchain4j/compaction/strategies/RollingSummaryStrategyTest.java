package io.quarkiverse.langchain4j.compaction.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.Fixtures;

class RollingSummaryStrategyTest {

    private final Fixtures.RecordingSummarizer summarizer = new Fixtures.RecordingSummarizer();

    private RollingSummaryStrategy strategy(int keepLastTurns) {
        return new RollingSummaryStrategy(new RollingSummaryStrategy.Options(keepLastTurns, 100), summarizer);
    }

    @Test
    void replacesOldTurnsWithOneSummary() {
        List<ChatMessage> history = Fixtures.conversation(6);
        List<ChatMessage> view = strategy(2).compact(history, Fixtures.context(10_000));

        List<Conversation.Turn> turns = Conversation.parse(history).turns();
        List<ChatMessage> expected = new ArrayList<>();
        expected.add(Fixtures.SYSTEM);
        expected.add(view.get(1));
        expected.addAll(turns.get(4).messages());
        expected.addAll(turns.get(5).messages());
        assertThat(view).isEqualTo(expected);
        assertThat(CompactionMessages.isSummary(view.get(1))).isTrue();
        // the 4 old turns, with their tool exchanges, went to the summarizer in one call
        assertThat(summarizer.calls).containsExactly(turns.subList(0, 4).stream().mapToInt(t -> t.messages().size()).sum());
    }

    @Test
    void foldsNewTurnsIncrementally() {
        RollingSummaryStrategy strategy = strategy(2);
        Fixtures.TestContext context = Fixtures.context(10_000);

        List<ChatMessage> first = strategy.compact(Fixtures.conversation(6), context);
        assertThat(summarizer.calls).hasSize(1);
        assertThat(CompactionMessages.isSummary(first.get(1))).isTrue();

        // same history again: cached
        strategy.compact(Fixtures.conversation(6), context);
        assertThat(summarizer.calls).hasSize(1);
        assertThat(context.notes).anyMatch(n -> n.contains("reused cached summary"));

        // two more turns: only the 2 newly aged turns are summarized, folded into the previous summary
        List<ChatMessage> second = strategy.compact(Fixtures.conversation(8), context);
        assertThat(summarizer.calls).hasSize(2);
        assertThat(summarizer.previous.get(1)).isNotNull();
        assertThat(CompactionMessages.text(second.get(1))).contains(" + ");
    }

    @Test
    void rebuildsTheSummaryWhenAnOldTurnChanged() {
        RollingSummaryStrategy strategy = strategy(2);
        Fixtures.TestContext context = Fixtures.context(10_000);
        strategy.compact(Fixtures.conversation(6), context);

        List<ChatMessage> edited = new ArrayList<>(Fixtures.conversation(7));
        edited.set(1, UserMessage.from("Question 0, edited by the application"));
        strategy.compact(edited, context);

        // the cached summary no longer matches the history: everything old is summarized again, from scratch
        assertThat(summarizer.calls).hasSize(2);
        assertThat(summarizer.previous.get(1)).isNull();
    }

    @Test
    void leavesShortConversationsUntouched() {
        List<ChatMessage> history = Fixtures.conversation(2);
        assertThat(strategy(2).compact(history, Fixtures.context(10_000))).isEqualTo(history);
        assertThat(summarizer.calls).isEmpty();
    }

    @Test
    void dropsOldTurnsWhenSummarizationFails() {
        RollingSummaryStrategy strategy = new RollingSummaryStrategy(new RollingSummaryStrategy.Options(2, 100),
                (previous, messages, maxWords) -> {
                    throw new IllegalStateException("model unavailable");
                });
        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> history = Fixtures.conversation(6);
        List<ChatMessage> view = strategy.compact(history, context);
        assertThat(view.stream().filter(CompactionMessages::isSummary)).isEmpty();
        assertThat(context.notes).anyMatch(n -> n.contains("summarization failed"))
                .anyMatch(n -> n.contains("dropped 4 turns without summary"));
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }
}
