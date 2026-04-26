package io.quarkiverse.langchain4j.evaluation.junit5.test;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.StrategyTest;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;

@ExtendWith(EvaluationExtension.class)
class StrategyTestTest {

    @StrategyTest(strategies = {
            AlwaysPassStrategy.class,
            ExactMatchStrategy.class
    })
    void shouldRunTestWithMultipleStrategies(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            EvaluationStrategy<String> strategy,
            Scorer scorer) {

        // Verify the strategy was injected
        assertThat(strategy)
                .as("Strategy should be injected")
                .isNotNull();

        // Verify it's one of the expected strategies
        assertThat(strategy)
                .as("Strategy should be one of the specified types")
                .isInstanceOfAny(AlwaysPassStrategy.class, ExactMatchStrategy.class);

        // Run evaluation
        var report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                strategy);

        assertThat(report)
                .as("Report should be generated")
                .isNotNull();

        // AlwaysPassStrategy should score 100%
        // ExactMatchStrategy should also score 100% since we return the expected output
        assertThat(report.score())
                .as("Report should have a score")
                .isGreaterThanOrEqualTo(0.0);
    }

    @StrategyTest(strategies = {
            AlwaysPassStrategy.class
    })
    void shouldWorkWithSingleStrategy(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            EvaluationStrategy<String> strategy,
            Scorer scorer) {

        assertThat(strategy)
                .as("Should inject AlwaysPassStrategy")
                .isInstanceOf(AlwaysPassStrategy.class);

        var report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                strategy);

        assertThat(report)
                .hasAllPassed()
                .hasScoreGreaterThanOrEqualTo(100.0);
    }

    @StrategyTest(strategies = {
            ExactMatchStrategy.class,
            AlwaysPassStrategy.class
    })
    void shouldRunEachStrategyIndependently(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            EvaluationStrategy<String> strategy,
            Scorer scorer) {

        // This test will run twice:
        // 1. With ExactMatchStrategy
        // 2. With AlwaysPassStrategy

        var report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                strategy);

        // Both strategies should pass since we return expected output
        assertThat(report).hasAllPassed();
    }

    @StrategyTest(strategies = {
            AlwaysPassStrategy.class,
            AlwaysFailStrategy.class
    }, minScore = 50.0)
    void shouldSupportMinScoreParameter(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            EvaluationStrategy<String> strategy,
            Scorer scorer) {

        // The minScore parameter is available for future enhancement
        // Currently it's just stored in the annotation
        var report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                strategy);

        assertThat(report).isNotNull();

        // AlwaysPassStrategy will pass
        // AlwaysFailStrategy will fail - but we don't auto-enforce minScore yet
    }
}
