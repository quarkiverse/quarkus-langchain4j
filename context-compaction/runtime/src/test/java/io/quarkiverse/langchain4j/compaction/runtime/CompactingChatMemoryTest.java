package io.quarkiverse.langchain4j.compaction.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.compaction.CompactionInspector;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Fixtures;
import io.quarkiverse.langchain4j.compaction.TokenBudget;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;
import io.quarkiverse.langchain4j.compaction.strategies.RollingSummaryStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.TokenWindowStrategy;

class CompactingChatMemoryTest {

    private InMemoryChatMemoryStore store;
    private CompactionInspector inspector;
    private List<CompactionReport> reports;

    @BeforeEach
    void init() {
        store = new InMemoryChatMemoryStore();
        inspector = new CompactionInspector();
        reports = new ArrayList<>();
    }

    private CompactingChatMemory memory(CompactionStrategy strategy, int contextWindow) {
        return memory(strategy, contextWindow, Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
    }

    private CompactingChatMemory memory(CompactionStrategy strategy, int contextWindow, Clock clock) {
        CompactingChatMemory.Components components = new CompactingChatMemory.Components("test", store, "test", strategy,
                new TokenBudget(contextWindow, 0, 0.8, 0.6), new TokenCounterProducer.HeuristicTokenCounter(), true,
                true, new ConversationStates(), inspector, true, new ToolOutputArchive(), reports::add, clock);
        return new CompactingChatMemory("c1", components);
    }

    /**
     * Mimics Quarkus LangChain4j's DefaultCommittableChatMemory: read the view once, append, write it all back.
     */
    private static void exchange(CompactingChatMemory memory, String question, String answer) {
        List<ChatMessage> working = memory.messages();
        working.add(UserMessage.from(question));
        working.add(AiMessage.from(answer));
        memory.set(working);
    }

    @Test
    void storesTheFullHistoryAndServesACompactedView() {
        Fixtures.RecordingSummarizer summarizer = new Fixtures.RecordingSummarizer();
        CompactingChatMemory memory = memory(
                new RollingSummaryStrategy(new RollingSummaryStrategy.Options(2, 100), summarizer), 300);
        memory.add(Fixtures.SYSTEM);
        for (int i = 0; i < 20; i++) {
            exchange(memory, "Question number " + i + " " + "about the order status ".repeat(3),
                    "Answer number " + i + " " + "the order has shipped ".repeat(3));
        }

        // the store keeps everything
        assertThat(store.getMessages("c1")).hasSize(41);
        // the view is compacted
        List<ChatMessage> view = memory.messages();
        assertThat(view.size()).isLessThan(41);
        assertThat(view.get(0)).isEqualTo(Fixtures.SYSTEM);
        assertThat(view.stream().filter(CompactionMessages::isSummary)).hasSize(1);
        assertThat(reports).isNotEmpty();
        assertThat(reports.get(reports.size() - 1).tokensAfter()).isLessThan(reports.get(reports.size() - 1).tokensBefore());
        assertThat(inspector.snapshot("c1")).hasValueSatisfying(s -> assertThat(s.history()).hasSize(41));
        // summaries are never written to the store
        assertThat(store.getMessages("c1")).noneMatch(CompactionMessages::isDerived);
    }

    @Test
    void doesNotCompactBelowTheTrigger() {
        CompactingChatMemory memory = memory(
                new RollingSummaryStrategy(new RollingSummaryStrategy.Options(2, 100),
                        new Fixtures.RecordingSummarizer()),
                100_000);
        memory.add(Fixtures.SYSTEM);
        exchange(memory, "hi", "hello");
        assertThat(memory.messages()).hasSize(3);
        assertThat(reports).isEmpty();
    }

    @Test
    void replacesTheSystemMessage() {
        CompactingChatMemory memory = memory((conv, c) -> conv.toMessages(), 100_000);
        memory.add(Fixtures.SYSTEM);
        exchange(memory, "hi", "hello");
        List<ChatMessage> working = memory.messages();
        working.set(0, SystemMessage.from("New instructions"));
        working.add(UserMessage.from("next"));
        memory.set(working);
        assertThat(store.getMessages("c1")).hasSize(4);
        assertThat(store.getMessages("c1").get(0)).isEqualTo(SystemMessage.from("New instructions"));
    }

    @Test
    void fallsBackToTokenWindowWhenAStrategyBreaksInvariants() {
        // a broken strategy dropping the system message
        CompactingChatMemory memory = memory((conv, c) -> {
            var m = conv.toMessages();
            return m.subList(1, m.size());
        }, 200);
        memory.add(Fixtures.SYSTEM);
        for (int i = 0; i < 10; i++) {
            exchange(memory, "question " + "x".repeat(100), "answer " + "y".repeat(100));
        }
        List<ChatMessage> view = memory.messages();
        assertThat(view.get(0)).isEqualTo(Fixtures.SYSTEM);
        assertThat(reports.get(reports.size() - 1).fallback()).isTrue();
    }

    @Test
    void clearRemovesEverything() {
        CompactingChatMemory memory = memory((conv, c) -> conv.toMessages(), 100_000);
        memory.add(Fixtures.SYSTEM);
        exchange(memory, "hi", "hello");
        memory.clear();
        assertThat(store.getMessages("c1")).isEmpty();
        assertThat(inspector.snapshot("c1")).isEmpty();
    }

    /**
     * Mimics a long tool loop within one AI service invocation: Quarkus LangChain4j buffers the full history plus the new
     * messages, asks for the view of that buffer before each model request, and writes the buffer back at the end.
     */
    @Test
    void compactsMessagesThatAreNotStoredYet() {
        CompactingChatMemory memory = memory(new TokenWindowStrategy(), 600);
        memory.add(Fixtures.SYSTEM);
        memory.add(UserMessage.from("Why is the payment service down?"));

        List<ChatMessage> buffer = memory.history();
        for (int i = 0; i < 10; i++) {
            buffer.add(AiMessage.from(Fixtures.request("call-" + i, "logs", "{}")));
            buffer.add(ToolExecutionResultMessage.from("call-" + i, "logs", "line " + i + " ".repeat(400)));
            List<ChatMessage> view = memory.view(buffer);
            assertThat(new TokenCounterProducer.HeuristicTokenCounter().count(view)).isLessThanOrEqualTo(480);
            assertThat(view.get(0)).isEqualTo(Fixtures.SYSTEM);
            assertThat(view).contains(UserMessage.from("Why is the payment service down?"));
            assertThat(view.get(view.size() - 1)).isSameAs(buffer.get(buffer.size() - 1));
        }
        assertThat(reports).isNotEmpty();
        // the view does not write anything
        assertThat(store.getMessages("c1")).hasSize(2);

        buffer.add(AiMessage.from("The disk is full."));
        memory.setHistory(buffer);
        assertThat(store.getMessages("c1")).hasSize(23).noneMatch(CompactionMessages::isDerived);
    }

    @Test
    void keepsTheTimestampsOfStoredAndBufferedMessages() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-20T12:00:00Z"));
        Map<String, Instant> seen = new HashMap<>();
        CompactionStrategy recording = (conv, context) -> {
            for (ChatMessage message : conv.toMessages()) {
                context.timestamp(message).ifPresent(t -> seen.put(CompactionMessages.text(message), t));
            }
            return conv.toMessages();
        };
        // a tiny window so that every view is a compaction
        CompactingChatMemory memory = memory(recording, 10, clock);
        UserMessage question = UserMessage.from("question");
        memory.add(question);

        clock.advance(Duration.ofMinutes(1));
        List<ChatMessage> buffer = memory.history();
        buffer.add(AiMessage.from("answer"));
        memory.view(buffer);

        clock.advance(Duration.ofMinutes(1));
        memory.view(buffer);
        memory.setHistory(buffer);

        clock.advance(Duration.ofMinutes(1));
        seen.clear();
        memory.messages();
        assertThat(seen).containsEntry("question", Instant.parse("2026-10-20T12:00:00Z"));
        // the time the answer was first seen, not the time it was written
        assertThat(seen).containsEntry("answer", Instant.parse("2026-10-20T12:01:00Z"));
    }

    /**
     * A caller using the plain ChatMemory contract writes back a compacted view that is not the last one handed out
     * (here because it was already written once): the summary is not stored and the compacted turns are kept.
     */
    @Test
    void writingBackACompactedViewKeepsTheHistory() {
        CompactingChatMemory memory = memory(
                new RollingSummaryStrategy(new RollingSummaryStrategy.Options(2, 100), new Fixtures.RecordingSummarizer()),
                300);
        memory.add(Fixtures.SYSTEM);
        for (int i = 0; i < 20; i++) {
            exchange(memory, "Question number " + i + " " + "about the order status ".repeat(3),
                    "Answer number " + i + " " + "the order has shipped ".repeat(3));
        }
        List<ChatMessage> view = memory.messages();
        assertThat(view).anyMatch(CompactionMessages::isSummary);
        memory.set(view);
        view.add(UserMessage.from("one more question"));
        memory.set(view);

        List<ChatMessage> history = store.getMessages("c1");
        assertThat(history).hasSize(42).noneMatch(CompactionMessages::isDerived);
        assertThat(history.get(0)).isEqualTo(Fixtures.SYSTEM);
        assertThat(history.get(41)).isEqualTo(UserMessage.from("one more question"));
    }

    /**
     * Like an output guardrail rewriting the answer: the caller replaces the last message of the view it read.
     */
    @Test
    void rewritingTheLastAnswerOfACompactedView() {
        CompactingChatMemory memory = memory(
                new RollingSummaryStrategy(new RollingSummaryStrategy.Options(2, 100), new Fixtures.RecordingSummarizer()),
                300);
        memory.add(Fixtures.SYSTEM);
        for (int i = 0; i < 20; i++) {
            exchange(memory, "Question number " + i + " " + "about the order status ".repeat(3),
                    "Answer number " + i + " " + "the order has shipped ".repeat(3));
        }
        List<ChatMessage> view = memory.messages();
        memory.add(UserMessage.from("ignored"));
        memory.set(store.getMessages("c1").subList(0, 41));
        // the memory no longer matches the view: set reconciles
        view.set(view.size() - 1, AiMessage.from("A better answer"));
        memory.set(view);

        List<ChatMessage> history = store.getMessages("c1");
        assertThat(history).hasSize(41).noneMatch(CompactionMessages::isDerived);
        assertThat(history.get(1)).isEqualTo(UserMessage.from("Question number 0 " + "about the order status ".repeat(3)));
        assertThat(history.get(40)).isEqualTo(AiMessage.from("A better answer"));
    }

    @Test
    void writingAnEmptyListClearsTheHistory() {
        CompactingChatMemory memory = memory((conv, c) -> conv.toMessages(), 100_000);
        memory.add(Fixtures.SYSTEM);
        exchange(memory, "hi", "hello");
        memory.set(List.of());
        assertThat(store.getMessages("c1")).isEmpty();
    }

    @Test
    void evictingTheStateOfAConversationReleasesItsSnapshotAndArchive() {
        ToolOutputArchive archive = new ToolOutputArchive();
        ConversationStates states = new ConversationStates(1, Duration.ofHours(1), Clock.systemUTC(), id -> {
            inspector.remove(id);
            archive.clear(id);
        });
        CompactingChatMemory.Components components = new CompactingChatMemory.Components("test", store, "test",
                (conv, c) -> conv.toMessages(), new TokenBudget(100_000, 0, 0.8, 0.6),
                new TokenCounterProducer.HeuristicTokenCounter(), true, true, states, inspector, true, archive,
                reports::add, Clock.systemUTC());
        CompactingChatMemory first = new CompactingChatMemory("first", components);
        first.add(UserMessage.from("hello"));
        first.messages();
        archive.put("first", "call-1", "output");
        assertThat(inspector.snapshot("first")).isPresent();

        CompactingChatMemory second = new CompactingChatMemory("second", components);
        second.add(UserMessage.from("hi"));

        assertThat(states.size()).isEqualTo(1);
        assertThat(inspector.snapshot("first")).isEmpty();
        assertThat(archive.get("first", "call-1")).isEmpty();
        // the history itself is untouched
        assertThat(store.getMessages("first")).hasSize(1);
        assertThat(first.messages()).hasSize(1);
    }

    @Test
    void recordsReportsButNoSnapshotWhenSnapshotsAreDisabled() {
        CompactingChatMemory.Components components = new CompactingChatMemory.Components("test", store, "test",
                new TokenWindowStrategy(), new TokenBudget(200, 0, 0.8, 0.6),
                new TokenCounterProducer.HeuristicTokenCounter(), true, true, new ConversationStates(), inspector,
                false, new ToolOutputArchive(), reports::add, Clock.systemUTC());
        CompactingChatMemory memory = new CompactingChatMemory("c1", components);
        for (int i = 0; i < 10; i++) {
            exchange(memory, "question " + "x".repeat(100), "answer " + "y".repeat(100));
        }
        memory.messages();
        assertThat(inspector.snapshot("c1")).isEmpty();
        assertThat(inspector.reports()).isNotEmpty();
    }

    @Test
    void cutsADegradedViewThatDoesNotFit() {
        CompactingChatMemory memory = memory(new RollingSummaryStrategy(new RollingSummaryStrategy.Options(2, 100),
                (previous, messages, maxWords) -> {
                    throw new IllegalStateException("model unavailable");
                }), 300);
        for (int i = 0; i < 10; i++) {
            exchange(memory, "question " + i + " " + "x".repeat(100), "answer " + i + " " + "y".repeat(100));
        }
        List<ChatMessage> view = memory.messages();

        CompactionReport report = reports.get(reports.size() - 1);
        assertThat(report.degraded()).isTrue();
        assertThat(report.fallback()).isFalse();
        assertThat(report.notes()).anyMatch(n -> n.contains("applying token-window"));
        assertThat(report.tokensAfter()).isLessThanOrEqualTo(new TokenBudget(300, 0, 0.8, 0.6).target());
        assertThat(view.get(0)).isInstanceOf(UserMessage.class);
        // the full history is still stored
        assertThat(store.getMessages("c1")).hasSize(20);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
