package io.quarkiverse.langchain4j.compaction.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Fixtures;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;

class PipelineStrategyTest {

    private final Fixtures.RecordingSummarizer summarizer = new Fixtures.RecordingSummarizer();
    private final List<String> ran = new ArrayList<>();

    /** wraps a step to record that it ran */
    private CompactionStrategy recorded(String id, CompactionStrategy step) {
        return (messages, context) -> {
            ran.add(id);
            return step.compact(messages, context);
        };
    }

    private PipelineStrategy pipeline() {
        Map<String, CompactionStrategy> steps = new LinkedHashMap<>();
        steps.put("tool-output-pruning", recorded("tool-output-pruning", new ToolOutputPruningStrategy(
                new ToolOutputPruningStrategy.Options(1, 200, 30), new ToolOutputArchive())));
        steps.put("rolling-summary", recorded("rolling-summary",
                new RollingSummaryStrategy(new RollingSummaryStrategy.Options(2, 100), summarizer)));
        steps.put("token-window", recorded("token-window", new TokenWindowStrategy()));
        return new PipelineStrategy(steps);
    }

    @Test
    void stopsAsSoonAsItFits() {
        List<ChatMessage> history = Fixtures.conversation(8);
        // generous budget: pruning tool outputs is enough, no summary needed
        Fixtures.TestContext context = Fixtures.context(700);
        List<ChatMessage> view = pipeline().compact(history, context);
        assertThat(ran).containsExactly("tool-output-pruning");
        assertThat(summarizer.calls).isEmpty();
        assertThat(context.fits(view)).isTrue();
        assertThat(context.notes).anyMatch(n -> n.startsWith("pipeline") && n.contains("tool-output-pruning"));
    }

    @Test
    void runsTheNextStepsWhenNeeded() {
        Fixtures.TestContext context = Fixtures.context(150);
        List<ChatMessage> view = pipeline().compact(Fixtures.conversation(8), context);
        assertThat(ran).containsExactly("tool-output-pruning", "rolling-summary", "token-window");
        assertThat(context.fits(view)).isTrue();
    }

    @Test
    void doesNothingWhenTheHistoryAlreadyFits() {
        List<ChatMessage> history = Fixtures.conversation(2);
        assertThat(pipeline().compact(history, Fixtures.context(100_000))).isEqualTo(history);
        assertThat(ran).isEmpty();
    }

    @Test
    void reportsWhenNoStepIsEnough() {
        Map<String, CompactionStrategy> steps = new LinkedHashMap<>();
        steps.put("duplicate-collapse", new DuplicateCollapseStrategy());
        Fixtures.TestContext context = Fixtures.context(10);
        new PipelineStrategy(steps).compact(Fixtures.conversation(4), context);
        assertThat(context.notes).anyMatch(n -> n.contains("still above target after all steps"));
    }

    @Test
    void describesItsSteps() {
        assertThat(pipeline().steps()).containsExactly("tool-output-pruning", "rolling-summary", "token-window");
        assertThat(pipeline().description()).contains("tool-output-pruning");
    }
}
