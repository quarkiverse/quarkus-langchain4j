package io.quarkiverse.langchain4j.compaction.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.Fixtures;
import io.quarkiverse.langchain4j.compaction.Summarizer;

class TimeDecayStrategyTest {

    private static final String LONG = "This answer is long on purpose. ".repeat(10);

    private final Fixtures.RecordingSummarizer summarizer = new Fixtures.RecordingSummarizer();
    private final Fixtures.TestContext context = Fixtures.context(10_000);
    private final List<ChatMessage> history = new ArrayList<>(List.of(Fixtures.SYSTEM));

    /** recent: 10 minutes, stale: 1 hour, aging messages truncated to 50 chars */
    private TimeDecayStrategy strategy(Summarizer summarizer) {
        return new TimeDecayStrategy(new TimeDecayStrategy.Options(Duration.ofMinutes(10), Duration.ofHours(1), 50,
                100), summarizer);
    }

    private void turn(String name, Duration age) {
        UserMessage question = UserMessage.from(name + " question");
        AiMessage answer = AiMessage.from(name + " answer. " + LONG);
        history.add(question);
        history.add(answer);
        context.timestamps.put(question, context.now.minus(age));
        context.timestamps.put(answer, context.now.minus(age));
    }

    @Test
    void summarizesStaleTurnsTruncatesAgingTurnsAndKeepsRecentTurns() {
        turn("stale-1", Duration.ofHours(3));
        turn("stale-2", Duration.ofHours(2));
        turn("aging", Duration.ofMinutes(30));
        turn("recent", Duration.ofMinutes(5));
        turn("last", Duration.ofMinutes(1));

        List<ChatMessage> view = strategy(summarizer).compact(history, context);

        assertThat(view).hasSize(8);
        assertThat(CompactionMessages.isSummary(view.get(1))).isTrue();
        assertThat(summarizer.calls).containsExactly(4);
        // aging turn: the short question is kept, the long answer is truncated
        assertThat(view.get(2)).isEqualTo(history.get(5));
        assertThat(CompactionMessages.kind(view.get(3))).isEqualTo(CompactionMessages.KIND_TRUNCATED);
        assertThat(CompactionMessages.text(view.get(3))).startsWith("aging answer.").contains("chars removed");
        // recent turns are verbatim
        assertThat(view.subList(4, 8)).isEqualTo(history.subList(7, 11));
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }

    @Test
    void neverTouchesTheLastTurnEvenIfOld() {
        turn("old", Duration.ofHours(5));
        turn("also old", Duration.ofHours(4));

        List<ChatMessage> view = strategy(summarizer).compact(history, context);
        assertThat(view).endsWith(history.get(3), history.get(4));
    }

    @Test
    void onlySummarizesTheOldestStretch() {
        turn("stale", Duration.ofHours(3));
        turn("aging", Duration.ofMinutes(30));
        // timestamps out of order (a turn restored from an older session): summarizing it would break the order
        turn("stale again", Duration.ofHours(2));
        turn("last", Duration.ofMinutes(1));

        List<ChatMessage> view = strategy(summarizer).compact(history, context);
        assertThat(summarizer.calls).containsExactly(2);
        assertThat(view).extracting(CompactionMessages::text).anyMatch(t -> t.startsWith("stale again question"));
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }

    @Test
    void dropsStaleTurnsWithoutSummarizer() {
        turn("stale", Duration.ofHours(3));
        turn("last", Duration.ofMinutes(1));

        List<ChatMessage> view = strategy(null).compact(history, context);
        assertThat(view).containsExactly(Fixtures.SYSTEM, history.get(3), history.get(4));
        assertThat(context.notes).anyMatch(n -> n.contains("dropped 1 stale turns"));
    }

    @Test
    void leavesTheHistoryAloneWithoutTimestamps() {
        List<ChatMessage> conversation = Fixtures.conversation(4);
        history.addAll(conversation.subList(1, conversation.size()));
        List<ChatMessage> view = strategy(summarizer).compact(history, context);
        assertThat(view).isEqualTo(history);
        assertThat(context.notes).anyMatch(n -> n.contains("no message timestamps known"));
    }
}
