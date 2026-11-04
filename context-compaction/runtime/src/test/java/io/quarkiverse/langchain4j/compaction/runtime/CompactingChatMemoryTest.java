package io.quarkiverse.langchain4j.compaction.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
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
        CompactingChatMemory.Components components = new CompactingChatMemory.Components("test", store, "test", strategy,
                new TokenBudget(contextWindow, 0, 0.8, 0.6), new TokenCounterProducer.HeuristicTokenCounter(), true,
                true, new ConversationStates(), inspector, new ToolOutputArchive(), reports::add,
                Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
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
}
