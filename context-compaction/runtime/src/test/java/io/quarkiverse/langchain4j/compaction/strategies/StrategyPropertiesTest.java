package io.quarkiverse.langchain4j.compaction.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import io.quarkiverse.langchain4j.compaction.CompactionInvariants;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.Fixtures;
import io.quarkiverse.langchain4j.compaction.RandomConversations;
import io.quarkiverse.langchain4j.compaction.TokenBudget;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;

/**
 * Properties every strategy must have, checked on {@value #SEEDS} generated conversations (see
 * {@link RandomConversations}) with budgets from generous to impossible. A failure message gives the seed, so it can be
 * reproduced with {@code RandomConversations.generate(seed)}.
 */
class StrategyPropertiesTest {

    static final int SEEDS = 300;
    static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    /**
     * Fresh instances for each conversation: strategies are stateless, but this keeps the test honest if one is not.
     */
    static Map<String, Supplier<CompactionStrategy>> strategies() {
        Map<String, Supplier<CompactionStrategy>> strategies = new LinkedHashMap<>();
        strategies.put(Strategies.MESSAGE_WINDOW, () -> new MessageWindowStrategy(7));
        strategies.put(Strategies.TOKEN_WINDOW, TokenWindowStrategy::new);
        strategies.put(Strategies.TOOL_OUTPUT_PRUNING, () -> new ToolOutputPruningStrategy(
                new ToolOutputPruningStrategy.Options(2, 200, 50), new ToolOutputArchive()));
        strategies.put(Strategies.DUPLICATE_COLLAPSE, DuplicateCollapseStrategy::new);
        strategies.put(Strategies.ROLLING_SUMMARY, () -> new RollingSummaryStrategy(
                new RollingSummaryStrategy.Options(3, 100), new Fixtures.RecordingSummarizer()));
        strategies.put(Strategies.ANCHORED_SUMMARY, () -> new AnchoredSummaryStrategy(
                new AnchoredSummaryStrategy.Options(1, 3, 100), new Fixtures.RecordingSummarizer()));
        strategies.put(Strategies.TIME_DECAY, () -> new TimeDecayStrategy(new TimeDecayStrategy.Options(
                Duration.ofMinutes(20), Duration.ofHours(2), 100, 100), new Fixtures.RecordingSummarizer()));
        strategies.put(Strategies.RELEVANCE, () -> new RelevanceStrategy(new RelevanceStrategy.Options(2, 2), null));
        strategies.put(Strategies.PIPELINE, StrategyPropertiesTest::defaultPipeline);
        return strategies;
    }

    static PipelineStrategy defaultPipeline() {
        Map<String, CompactionStrategy> steps = new LinkedHashMap<>();
        steps.put(Strategies.TOOL_OUTPUT_PRUNING, new ToolOutputPruningStrategy(
                new ToolOutputPruningStrategy.Options(2, 200, 50), new ToolOutputArchive()));
        steps.put(Strategies.DUPLICATE_COLLAPSE, new DuplicateCollapseStrategy());
        steps.put(Strategies.ROLLING_SUMMARY, new RollingSummaryStrategy(new RollingSummaryStrategy.Options(3, 100),
                new Fixtures.RecordingSummarizer()));
        steps.put(Strategies.TOKEN_WINDOW, new TokenWindowStrategy());
        return new PipelineStrategy(steps);
    }

    static List<String> ids() {
        return new ArrayList<>(strategies().keySet());
    }

    /**
     * A context with a target between 10% and 100% of the history, and the generated message timestamps.
     */
    static Fixtures.TestContext context(long seed, RandomConversations.Generated generated) {
        Fixtures.TestContext probe = Fixtures.context(1);
        int tokens = probe.tokens(generated.messages());
        int target = Math.max(1, (int) (tokens * (0.1 + (seed % 10) / 10.0)));
        Fixtures.TestContext context = Fixtures.context(new TokenBudget(target, 0, 1.0, 1.0));
        context.now = NOW;
        for (int i = 0; i < generated.messages().size(); i++) {
            context.timestamps.put(generated.messages().get(i), generated.timestamps().get(i));
        }
        return context;
    }

    @Test
    void generatedConversationsAreValid() {
        for (long seed = 0; seed < SEEDS; seed++) {
            List<ChatMessage> messages = RandomConversations.generate(seed);
            assertThat(CompactionInvariants.violations(messages, messages)).as("seed %d", seed).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("ids")
    void keepsTheInvariants(String id) {
        List<String> failures = new ArrayList<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            RandomConversations.Generated generated = RandomConversations.generate(seed, NOW);
            List<ChatMessage> history = generated.messages();
            List<ChatMessage> copy = List.copyOf(history);
            Fixtures.TestContext context = context(seed, generated);

            List<ChatMessage> view = strategies().get(id).get().compact(history, context);

            if (!history.equals(copy)) {
                failures.add("seed " + seed + ": input modified");
            }
            List<String> violations = CompactionInvariants.violations(history, view);
            if (!violations.isEmpty()) {
                failures.add("seed " + seed + ": " + violations);
            }
            if (!(view.get(0) instanceof SystemMessage)) {
                failures.add("seed " + seed + ": view does not start with the system message");
            }
            ChatMessage question = latestQuestion(history);
            if (!view.contains(question)) {
                failures.add("seed " + seed + ": latest user message not kept verbatim");
            }
        }
        assertThat(failures).as(id).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("ids")
    void neverGrowsTheHistory(String id) {
        List<String> failures = new ArrayList<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            RandomConversations.Generated generated = RandomConversations.generate(seed, NOW);
            Fixtures.TestContext context = context(seed, generated);
            List<ChatMessage> view = strategies().get(id).get().compact(generated.messages(), context);
            int before = context.tokens(generated.messages());
            int after = context.tokens(view);
            if (after > before) {
                failures.add("seed " + seed + ": " + before + " -> " + after + " tokens " + context.notes);
            }
        }
        assertThat(failures).as(id).isEmpty();
    }

    /**
     * The pipeline feeds a step with the output of the previous one, so every strategy must accept any other
     * strategy's view (summaries, pruned or truncated messages) and keep the invariants against the original history.
     */
    @ParameterizedTest
    @MethodSource("ids")
    void acceptsTheOutputOfAnyOtherStrategy(String id) {
        List<String> failures = new ArrayList<>();
        for (long seed = 0; seed < SEEDS; seed += 3) {
            for (String first : ids()) {
                RandomConversations.Generated generated = RandomConversations.generate(seed, NOW);
                Fixtures.TestContext context = context(seed, generated);
                List<ChatMessage> history = generated.messages();
                List<ChatMessage> intermediate = strategies().get(first).get().compact(history, context);
                List<ChatMessage> view = strategies().get(id).get().compact(intermediate, context);
                List<String> violations = CompactionInvariants.violations(history, view);
                if (!violations.isEmpty()) {
                    failures.add("seed " + seed + ", after " + first + ": " + violations);
                }
            }
        }
        assertThat(failures).as(id).isEmpty();
    }

    /**
     * The token window is the safety net: it always fits the target, unless the latest user message and the newest
     * segment alone are larger.
     */
    @ParameterizedTest
    @MethodSource("safetyNets")
    void safetyNetsFitTheTarget(String id) {
        List<String> failures = new ArrayList<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            RandomConversations.Generated generated = RandomConversations.generate(seed, NOW);
            Fixtures.TestContext context = context(seed, generated);
            List<ChatMessage> view = strategies().get(id).get().compact(generated.messages(), context);
            if (!context.fits(view) && Conversation.parse(view).segments().size() > 2) {
                failures.add("seed " + seed + ": " + context.tokens(view) + " tokens for a target of "
                        + context.budget().target());
            }
        }
        assertThat(failures).as(id).isEmpty();
    }

    static List<String> safetyNets() {
        return List.of(Strategies.TOKEN_WINDOW, Strategies.PIPELINE);
    }

    private static ChatMessage latestQuestion(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage && !CompactionMessages.isDerived(messages.get(i))) {
                return messages.get(i);
            }
        }
        throw new IllegalStateException("no user message");
    }
}
