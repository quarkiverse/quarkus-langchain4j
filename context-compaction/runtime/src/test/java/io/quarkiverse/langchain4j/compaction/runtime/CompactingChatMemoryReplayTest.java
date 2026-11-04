package io.quarkiverse.langchain4j.compaction.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkiverse.langchain4j.compaction.CompactionInspector;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Dataset;
import io.quarkiverse.langchain4j.compaction.Fixtures;
import io.quarkiverse.langchain4j.compaction.TokenBudget;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;
import io.quarkiverse.langchain4j.compaction.strategies.DuplicateCollapseStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.PipelineStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.RollingSummaryStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.Strategies;
import io.quarkiverse.langchain4j.compaction.strategies.TokenWindowStrategy;
import io.quarkiverse.langchain4j.compaction.strategies.ToolOutputPruningStrategy;

/**
 * Replays the recorded conversation call by call through a {@link CompactingChatMemory}, the way Quarkus
 * LangChain4j uses it: each AI service call reads the view once, appends the user message, the tool exchanges and the
 * answer, and writes the list back.
 */
class CompactingChatMemoryReplayTest {

    static final TokenBudget BUDGET = new TokenBudget(8000, 1000, 0.8, 0.6);

    private InMemoryChatMemoryStore store;
    private CompactionInspector inspector;
    private List<CompactionReport> reports;
    private Fixtures.RecordingSummarizer summarizer;

    @BeforeEach
    void init() {
        store = new InMemoryChatMemoryStore();
        inspector = new CompactionInspector();
        reports = new ArrayList<>();
        summarizer = new Fixtures.RecordingSummarizer();
    }

    private CompactingChatMemory memory(CompactionStrategy strategy, TokenBudget budget) {
        CompactingChatMemory.Components components = new CompactingChatMemory.Components("test", store, "replay", strategy,
                budget, new TokenCounterProducer.HeuristicTokenCounter(), true, true, new ConversationStates(),
                inspector, new ToolOutputArchive(), reports::add,
                Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC));
        return new CompactingChatMemory("replay", components);
    }

    private PipelineStrategy defaultPipeline() {
        return pipeline(true);
    }

    private PipelineStrategy pipeline(boolean collapseDuplicates) {
        Map<String, CompactionStrategy> steps = new LinkedHashMap<>();
        steps.put(Strategies.TOOL_OUTPUT_PRUNING, new ToolOutputPruningStrategy(
                new ToolOutputPruningStrategy.Options(2, 200, 120), new ToolOutputArchive()));
        if (collapseDuplicates) {
            steps.put(Strategies.DUPLICATE_COLLAPSE, new DuplicateCollapseStrategy());
        }
        steps.put(Strategies.ROLLING_SUMMARY,
                new RollingSummaryStrategy(new RollingSummaryStrategy.Options(4, 250), summarizer));
        steps.put(Strategies.TOKEN_WINDOW, new TokenWindowStrategy());
        return new PipelineStrategy(steps);
    }

    /**
     * Splits the recording into AI service calls: a user message and everything until the next one.
     */
    static List<List<ChatMessage>> calls(List<ChatMessage> conversation) {
        List<List<ChatMessage>> calls = new ArrayList<>();
        for (ChatMessage message : conversation) {
            if (message instanceof UserMessage || calls.isEmpty()) {
                calls.add(new ArrayList<>());
            }
            calls.get(calls.size() - 1).add(message);
        }
        return calls;
    }

    @Test
    void keepsTheFullHistoryWhileServingValidViews() {
        List<ChatMessage> recording = Dataset.supportConversation();
        CompactingChatMemory memory = memory(defaultPipeline(), BUDGET);
        memory.add(recording.get(0));

        int compactions = 0;
        for (List<ChatMessage> call : calls(recording.subList(1, recording.size()))) {
            List<ChatMessage> working = memory.messages();
            if (inspector.snapshot("replay").orElseThrow().report() != null) {
                compactions++;
                List<ChatMessage> history = store.getMessages("replay");
                assertThat(CompactionInvariants.violations(history, working)).isEmpty();
                assertThat(BUDGET.exceeded(new TokenCounterProducer.HeuristicTokenCounter().count(working)))
                        .as("a view above the trigger would compact again on the next read").isFalse();
            }
            working.addAll(call);
            memory.set(working);
        }

        // the store holds exactly the recording: nothing lost, nothing derived
        assertThat(store.getMessages("replay")).isEqualTo(recording);
        assertThat(compactions).isGreaterThan(1);
        assertThat(reports).isNotEmpty().noneMatch(CompactionReport::fallback);
    }

    private void replay(CompactingChatMemory memory) {
        List<ChatMessage> recording = Dataset.supportConversation();
        memory.add(recording.get(0));
        for (List<ChatMessage> call : calls(recording.subList(1, recording.size()))) {
            List<ChatMessage> working = memory.messages();
            working.addAll(call);
            memory.set(working);
        }
    }

    private void assertSummarizedIncrementally() {
        assertThat(summarizer.calls).hasSizeGreaterThan(1);
        // the first call builds the summary, later calls fold new turns into it
        assertThat(summarizer.previous.subList(1, summarizer.previous.size())).doesNotContainNull();
        assertThat(summarizer.calls.stream().mapToInt(Integer::intValue).sum())
                .as("messages sent to the summarizer").isLessThanOrEqualTo(Dataset.supportConversation().size());
    }

    @Test
    void summarizesEachTurnOnce() {
        replay(memory(pipeline(false), new TokenBudget(4000, 500, 0.8, 0.6)));
        assertSummarizedIncrementally();
    }

    /**
     * When a polling call repeats, duplicate-collapse removes its earlier occurrences, including those in turns the
     * summary already covers. The summary fingerprint no longer matches and the whole summary is rebuilt from scratch
     * (twice on the recording: 60, then 86 messages sent to the summarizer again).
     */
    @Test
    @Disabled("Known issue: duplicate-collapse before rolling-summary invalidates the cached summary")
    void summarizesEachTurnOnceWithTheDefaultPipeline() {
        replay(memory(defaultPipeline(), new TokenBudget(4000, 500, 0.8, 0.6)));
        assertSummarizedIncrementally();
    }

    /**
     * Two AI service calls on the same conversation at the same time (two browser tabs, a retry) both read the same
     * compacted view. The first write appends its messages; the second no longer matches the last view handed out,
     * takes the "caller rewrote the memory" path, and replaces the stored history with its compacted view.
     */
    @Test
    @Disabled("Known issue: concurrent calls replace the full history with a compacted view, see CompactingChatMemory#set")
    void concurrentCallsDoNotLoseTheHistory() {
        CompactingChatMemory memory = memory(
                new RollingSummaryStrategy(new RollingSummaryStrategy.Options(2, 100), summarizer),
                new TokenBudget(300, 0, 0.8, 0.6));
        memory.add(Fixtures.SYSTEM);
        for (int i = 0; i < 20; i++) {
            List<ChatMessage> working = memory.messages();
            working.add(UserMessage.from("Question " + i + " about the order status, again and again"));
            working.add(AiMessage.from("Answer " + i + ": the order has shipped, as said before"));
            memory.set(working);
        }
        assertThat(store.getMessages("replay")).hasSize(41);

        List<ChatMessage> first = memory.messages();
        List<ChatMessage> second = memory.messages();
        assertThat(first).anyMatch(CompactionMessages::isSummary);
        first.add(UserMessage.from("first tab"));
        first.add(AiMessage.from("first answer"));
        second.add(UserMessage.from("second tab"));
        second.add(AiMessage.from("second answer"));
        memory.set(first);
        memory.set(second);

        assertThat(store.getMessages("replay")).hasSizeGreaterThanOrEqualTo(43).noneMatch(CompactionMessages::isDerived);
    }
}
