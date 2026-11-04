package io.quarkiverse.langchain4j.compaction.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.Fixtures;

class MessageWindowStrategyTest {

    @Test
    void keepsTheLastMessagesAndTheSystemMessage() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        for (int i = 0; i < 10; i++) {
            history.add(UserMessage.from("question " + i));
            history.add(AiMessage.from("answer " + i));
        }
        List<ChatMessage> view = new MessageWindowStrategy(4).compact(history, Fixtures.context(10_000));
        // the system message does not count in the window
        assertThat(view).containsExactlyElementsOf(List.of(Fixtures.SYSTEM, history.get(17), history.get(18),
                history.get(19), history.get(20)));
    }

    @Test
    void neverSplitsAToolExchange() {
        List<ChatMessage> history = Fixtures.conversation(4);
        // 4 messages from the end lands in the middle of the tool exchange of turn 2
        List<ChatMessage> view = new MessageWindowStrategy(4).compact(history, Fixtures.context(10_000));
        assertThat(view.get(1)).isNotInstanceOf(ToolExecutionResultMessage.class);
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }

    @Test
    void dropsAParallelToolExchangeAsAWhole() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("compare my two orders"));
        history.add(AiMessage.from(Fixtures.request("a", "lookupOrder", "{\"order\":1}"),
                Fixtures.request("b", "lookupOrder", "{\"order\":2}")));
        history.add(ToolExecutionResultMessage.from("a", "lookupOrder", "order 1 shipped"));
        history.add(ToolExecutionResultMessage.from("b", "lookupOrder", "order 2 pending"));
        history.add(AiMessage.from("Order 1 shipped, order 2 is pending."));
        history.add(UserMessage.from("thanks"));
        history.add(AiMessage.from("You're welcome."));

        // a window of 4 would cut the 3-message exchange: it is dropped entirely
        List<ChatMessage> view = new MessageWindowStrategy(4).compact(history, Fixtures.context(10_000));
        assertThat(view).noneMatch(m -> m instanceof ToolExecutionResultMessage);
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }

    @Test
    void keepsTheLatestQuestionWhenItsToolCallsFillTheWindow() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("an older question"));
        history.add(AiMessage.from("an older answer"));
        UserMessage question = UserMessage.from("where are all my parcels?");
        history.add(question);
        for (int i = 0; i < 4; i++) {
            history.add(AiMessage.from(Fixtures.request("c" + i, "track", "{\"parcel\":" + i + "}")));
            history.add(ToolExecutionResultMessage.from("c" + i, "track", "parcel " + i + " in transit"));
        }

        Fixtures.TestContext context = Fixtures.context(10_000);
        List<ChatMessage> view = new MessageWindowStrategy(4).compact(history, context);
        assertThat(view).contains(question).doesNotContain(history.get(1));
        assertThat(view.get(1)).isEqualTo(question);
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
        assertThat(context.notes).anyMatch(n -> n.contains("kept the latest user message"));
    }

    @Test
    void keepsTheNewestSegmentEvenIfLargerThanTheWindow() {
        List<ChatMessage> history = Fixtures.conversation(1); // user, tool call, tool result, answer
        List<ChatMessage> view = new MessageWindowStrategy(1).compact(history, Fixtures.context(10_000));
        assertThat(view).containsExactly(Fixtures.SYSTEM, history.get(1), history.get(history.size() - 1));
    }

    @Test
    void leavesShortHistoriesUntouched() {
        List<ChatMessage> history = Fixtures.conversation(2);
        Fixtures.TestContext context = Fixtures.context(10_000);
        assertThat(new MessageWindowStrategy(50).compact(history, context)).isEqualTo(history);
        assertThat(context.notes).isEmpty();
    }
}
