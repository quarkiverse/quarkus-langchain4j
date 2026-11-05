package io.quarkiverse.langchain4j.compaction.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.compaction.CompactionInspector;
import io.quarkiverse.langchain4j.compaction.TokenBudget;

class RetentionTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    private InMemoryChatMemoryStore store;
    private CompactingChatMemoryTest.MutableClock clock;

    @BeforeEach
    void init() {
        store = new InMemoryChatMemoryStore();
        clock = new CompactingChatMemoryTest.MutableClock(NOW);
    }

    private CompactingChatMemory memory(Retention retention) {
        CompactingChatMemory.Components components = new CompactingChatMemory.Components("test", store, "test",
                (conversation, context) -> conversation.toMessages(), new TokenBudget(1_000_000, 0, 0.8, 0.6),
                new TokenCounterProducer.HeuristicTokenCounter(), true, true,
                new ConversationStates(100, Duration.ofHours(1), Clock.systemUTC(), id -> {
                }), new CompactionInspector(), false, new InMemoryToolOutputArchive(), r -> {
                }, clock, retention);
        return new CompactingChatMemory("c1", components);
    }

    private static Retention maxMessages(int max) {
        return new Retention(OptionalInt.of(max), Optional.empty());
    }

    private static Retention maxAge(Duration age) {
        return new Retention(OptionalInt.empty(), Optional.of(age));
    }

    private static List<ChatMessage> turns(int count) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            messages.add(UserMessage.from("q" + i));
            messages.add(AiMessage.from("a" + i));
        }
        return messages;
    }

    private static List<Instant> unknown(int size) {
        return Arrays.asList(new Instant[size]);
    }

    @Test
    void keepsEverythingByDefault() {
        assertThat(Retention.NONE.toDelete(turns(100), unknown(200), NOW)).isZero();
    }

    @Test
    void deletesWholeTurnsDownToEightyPercentOfTheLimit() {
        // 20 messages, limit 10: trim down to 8 messages, i.e. delete 6 turns
        assertThat(maxMessages(10).toDelete(turns(10), unknown(20), NOW)).isEqualTo(12);
        // under the limit: nothing
        assertThat(maxMessages(10).toDelete(turns(5), unknown(10), NOW)).isZero();
    }

    @Test
    void neverSplitsATurnNorDeletesTheLastOne() {
        List<ChatMessage> history = new ArrayList<>();
        history.add(SystemMessage.from("system"));
        history.add(UserMessage.from("q0"));
        ToolExecutionRequest request = ToolExecutionRequest.builder().id("1").name("tool").arguments("{}").build();
        for (int i = 0; i < 10; i++) {
            history.add(AiMessage.from(request));
            history.add(ToolExecutionResultMessage.from(request, "result " + i));
        }
        history.add(AiMessage.from("a0"));
        history.add(UserMessage.from("q1"));
        history.add(AiMessage.from("a1"));
        // the first turn has 22 messages: deleting it is the only cut, even if it goes below the target
        assertThat(maxMessages(4).toDelete(history, unknown(history.size()), NOW)).isEqualTo(22);
        // a single (huge) turn is kept
        List<ChatMessage> single = history.subList(0, 23);
        assertThat(maxMessages(4).toDelete(single, unknown(single.size()), NOW)).isZero();
    }

    @Test
    void deletesTheTurnsOlderThanTheMaxAgeAndStopsAtAnUnknownAge() {
        List<ChatMessage> history = turns(4);
        List<Instant> timestamps = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Instant time = NOW.minus(Duration.ofDays(4 - i));
            timestamps.add(time);
            timestamps.add(time.plusSeconds(1));
        }
        // turns from 4 and 3 days ago are deleted, the one from 2 days ago is kept
        assertThat(maxAge(Duration.ofDays(2).plusHours(1)).toDelete(history, timestamps, NOW)).isEqualTo(4);
        // the last turn is kept even if expired
        assertThat(maxAge(Duration.ofMinutes(1)).toDelete(history, timestamps, NOW)).isEqualTo(6);
        // an unknown age stops the deletion
        timestamps.set(2, null);
        assertThat(maxAge(Duration.ofMinutes(1)).toDelete(history, timestamps, NOW)).isEqualTo(2);
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> maxMessages(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> maxAge(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theMemoryAppliesTheRetentionWhenWriting() {
        CompactingChatMemory memory = memory(maxMessages(10));
        memory.add(SystemMessage.from("system"));
        for (int i = 0; i < 6; i++) {
            memory.add(UserMessage.from("q" + i));
            memory.add(AiMessage.from("a" + i));
        }
        // 12 messages > 10: trimmed to 8, the system message is kept
        List<ChatMessage> stored = store.getMessages("c1");
        assertThat(stored).hasSize(9);
        assertThat(stored.get(0)).isEqualTo(SystemMessage.from("system"));
        assertThat(stored.get(1)).isEqualTo(UserMessage.from("q2"));

        // AI service path: buffered history written back at the end of the invocation
        List<ChatMessage> buffer = memory.history();
        buffer.addAll(turns(2));
        memory.setHistory(buffer);
        assertThat(store.getMessages("c1")).hasSize(9);
    }

    @Test
    void theMemoryDeletesExpiredTurns() {
        CompactingChatMemory memory = memory(maxAge(Duration.ofHours(1)));
        memory.add(UserMessage.from("old question"));
        memory.add(AiMessage.from("old answer"));
        clock.advance(Duration.ofHours(2));
        memory.add(UserMessage.from("new question"));
        assertThat(store.getMessages("c1")).containsExactly(UserMessage.from("new question"));
    }

    @Test
    void writingBackAStaleViewOrBufferDoesNotRestoreExpiredTurns() {
        CompactingChatMemory memory = memory(maxAge(Duration.ofHours(1)));
        memory.add(UserMessage.from("q0"));
        memory.add(AiMessage.from("a0"));
        List<ChatMessage> view = memory.messages();
        List<ChatMessage> buffer = memory.history();

        clock.advance(Duration.ofHours(2));
        // another call: the old turn expires
        memory.add(UserMessage.from("q1"));
        memory.add(AiMessage.from("a1"));
        assertThat(store.getMessages("c1")).containsExactly(UserMessage.from("q1"), AiMessage.from("a1"));

        // ChatMemory#set with the stale view
        view.add(UserMessage.from("q2"));
        view.add(AiMessage.from("a2"));
        memory.set(view);
        assertThat(store.getMessages("c1")).containsExactly(UserMessage.from("q1"), AiMessage.from("a1"),
                UserMessage.from("q2"), AiMessage.from("a2"));

        // AI service commit with the stale buffer (last writer wins, but the expired turn stays deleted)
        buffer.add(UserMessage.from("q3"));
        buffer.add(AiMessage.from("a3"));
        memory.setHistory(buffer);
        assertThat(store.getMessages("c1")).containsExactly(UserMessage.from("q3"), AiMessage.from("a3"));
    }

    @Test
    void writingBackAViewThatStillHoldsDeletedMessagesDoesNotRestoreThem() {
        CompactingChatMemory memory = memory(maxMessages(4));
        memory.add(UserMessage.from("q0"));
        memory.add(AiMessage.from("a0"));
        memory.add(UserMessage.from("q1"));
        memory.add(AiMessage.from("a1"));
        List<ChatMessage> view = memory.messages();

        // another call adds a turn, which triggers the retention
        memory.add(UserMessage.from("q2"));
        memory.add(AiMessage.from("a2"));
        // 5 messages > 4 when q2 was added: trimmed to 3
        assertThat(store.getMessages("c1")).containsExactly(UserMessage.from("q1"), AiMessage.from("a1"),
                UserMessage.from("q2"), AiMessage.from("a2"));

        // the first caller writes back its (stale) view plus its new turn
        view.add(UserMessage.from("q3"));
        view.add(AiMessage.from("a3"));
        memory.set(view);
        // q0 and a0 are not restored; q1 ... q3 make 6 messages, trimmed to the last turn
        assertThat(store.getMessages("c1")).containsExactly(UserMessage.from("q3"), AiMessage.from("a3"));
    }
}
