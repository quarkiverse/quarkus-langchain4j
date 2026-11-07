package io.quarkiverse.langchain4j.compaction.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.Fixtures;

class TokenWindowStrategyTest {

    @Test
    void fitsTheTarget() {
        List<ChatMessage> history = Fixtures.conversation(20);
        Fixtures.TestContext context = Fixtures.context(800);
        List<ChatMessage> view = new TokenWindowStrategy().compact(history, context);
        assertThat(context.tokens(view)).isLessThanOrEqualTo(800);
        assertThat(view.get(view.size() - 1)).isEqualTo(history.get(history.size() - 1));
        assertThat(context.notes).anyMatch(n -> n.contains("to fit 800 tokens"));
    }

    @Test
    void countsTheSystemMessageInTheBudget() {
        List<ChatMessage> small = Fixtures.conversation(20);
        List<ChatMessage> large = new ArrayList<>(small);
        large.set(0, SystemMessage.from("You are a support agent. " + "Follow the policy. ".repeat(100)));

        List<ChatMessage> withSmall = new TokenWindowStrategy().compact(small, Fixtures.context(1500));
        Fixtures.TestContext context = Fixtures.context(1500);
        List<ChatMessage> withLarge = new TokenWindowStrategy().compact(large, context);
        assertThat(withLarge.size()).isLessThan(withSmall.size());
        assertThat(context.tokens(withLarge)).isLessThanOrEqualTo(1500);
    }

    @Test
    void keepsTheNewestSegmentEvenIfItAloneExceedsTheTarget() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("short question"));
        AiMessage answer = AiMessage.from("a very long answer ".repeat(200));
        history.add(answer);

        List<ChatMessage> view = new TokenWindowStrategy().compact(history, Fixtures.context(50));
        assertThat(view).containsExactly(Fixtures.SYSTEM, history.get(1), answer);
    }

    @Test
    void reservesRoomForTheLatestQuestion() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(Fixtures.SYSTEM);
        history.add(UserMessage.from("an older question"));
        history.add(AiMessage.from("an older answer"));
        UserMessage question = UserMessage.from("where are all my parcels? " + "please check each one. ".repeat(5));
        history.add(question);
        for (int i = 0; i < 6; i++) {
            history.add(AiMessage.from(Fixtures.request("c" + i, "track", "{\"parcel\":" + i + "}")));
            history.add(ToolExecutionResultMessage.from("c" + i, "track", "parcel " + i + " " + "x".repeat(200)));
        }

        Fixtures.TestContext context = Fixtures.context(250);
        List<ChatMessage> view = new TokenWindowStrategy().compact(history, context);
        assertThat(view.get(1)).isEqualTo(question);
        assertThat(view.get(view.size() - 1)).isEqualTo(history.get(history.size() - 1));
        assertThat(context.fits(view)).isTrue();
        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
    }
}
