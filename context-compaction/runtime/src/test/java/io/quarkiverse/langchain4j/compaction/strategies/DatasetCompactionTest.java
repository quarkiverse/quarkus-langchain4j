package io.quarkiverse.langchain4j.compaction.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Dataset;
import io.quarkiverse.langchain4j.compaction.Fixtures;
import io.quarkiverse.langchain4j.compaction.TokenBudget;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;

/**
 * Runs every strategy, with the default configuration, on the recorded 127-message support conversation and pins
 * what it keeps, so a change in a strategy's behavior shows up here. The numbers are published in the extension.
 */
class DatasetCompactionTest {

    /** the demo budget: 8000-token window, 1000 reserved for the completion, target 0.6 of the rest = 4200 */
    static final TokenBudget BUDGET = new TokenBudget(8000, 1000, 0.8, 0.6);

    static Map<String, CompactionStrategy> defaults() {
        Fixtures.RecordingSummarizer summarizer = new Fixtures.RecordingSummarizer();
        ToolOutputArchive archive = new ToolOutputArchive();
        Map<String, CompactionStrategy> strategies = new LinkedHashMap<>();
        strategies.put(Strategies.MESSAGE_WINDOW, new MessageWindowStrategy(20));
        strategies.put(Strategies.TOKEN_WINDOW, new TokenWindowStrategy());
        strategies.put(Strategies.TOOL_OUTPUT_PRUNING,
                new ToolOutputPruningStrategy(new ToolOutputPruningStrategy.Options(2, 200, 120), archive));
        strategies.put(Strategies.DUPLICATE_COLLAPSE, new DuplicateCollapseStrategy());
        strategies.put(Strategies.ROLLING_SUMMARY,
                new RollingSummaryStrategy(new RollingSummaryStrategy.Options(4, 250), summarizer));
        strategies.put(Strategies.ANCHORED_SUMMARY,
                new AnchoredSummaryStrategy(new AnchoredSummaryStrategy.Options(1, 4, 250), summarizer));
        strategies.put(Strategies.TIME_DECAY, new TimeDecayStrategy(
                new TimeDecayStrategy.Options(Duration.ofMinutes(15), Duration.ofHours(2), 400, 250), summarizer));
        strategies.put(Strategies.RELEVANCE, new RelevanceStrategy(new RelevanceStrategy.Options(3, 4), null));
        Map<String, CompactionStrategy> steps = new LinkedHashMap<>();
        for (String step : List.of(Strategies.TOOL_OUTPUT_PRUNING, Strategies.DUPLICATE_COLLAPSE,
                Strategies.ROLLING_SUMMARY, Strategies.TOKEN_WINDOW)) {
            steps.put(step, strategies.get(step));
        }
        strategies.put(Strategies.PIPELINE, new PipelineStrategy(steps));
        return strategies;
    }

    @ParameterizedTest
    @CsvSource({
            // id, messages and tokens without the system message
            "tool-output-pruning, 127, 4092",
            "duplicate-collapse,  111, 13389",
            "token-window,         38, 3958",
            "relevance,            35, 1548",
            "anchored-summary,     20, 1348",
            "rolling-summary,      18, 1257",
            "message-window,       20, 1743",
            // pruning alone fits the 4200-token target: the pipeline stops there
            "pipeline,            127, 4092",
            // no timestamps in the recording: time decay keeps everything
            "time-decay,          127, 13616",
    })
    void keepsWhatTheReadmeSays(String id, int messages, int tokens) {
        List<ChatMessage> history = Dataset.supportConversation();
        Fixtures.TestContext context = Fixtures.context(BUDGET);
        int system = context.tokens(List.of(Fixtures.SYSTEM));
        assertThat(history).hasSize(128);
        assertThat(context.tokens(history) - system).isEqualTo(13_616);

        List<ChatMessage> view = defaults().get(id).compact(history, context);

        assertThat(CompactionInvariants.violations(history, view)).isEmpty();
        assertThat(view.size() - 1).as("messages").isEqualTo(messages);
        assertThat(context.tokens(view) - system).as("tokens").isEqualTo(tokens);
    }
}
