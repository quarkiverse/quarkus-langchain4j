package io.quarkiverse.langchain4j.evaluation.junit5.it;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationDisplayNameGenerator;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.StrategyTest;
import io.quarkiverse.langchain4j.evaluation.junit5.it.strategies.QuarkusAlwaysPassStrategy;
import io.quarkiverse.langchain4j.evaluation.junit5.it.strategies.QuarkusExactMatchStrategy;
import io.quarkiverse.langchain4j.evaluation.junit5.test.AlwaysPassStrategy;
import io.quarkiverse.langchain4j.evaluation.junit5.test.ExactMatchStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@ExtendWith(EvaluationExtension.class)
@DisplayNameGeneration(EvaluationDisplayNameGenerator.class)
class StrategyTestIT {

    @StrategyTest(strategies = {
            AlwaysPassStrategy.class,
            ExactMatchStrategy.class
    })
    void shouldRunWithMultipleStrategiesInQuarkus(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            EvaluationStrategy<String> strategy,
            Scorer scorer) {

        assertThat(strategy)
                .as("Strategy should be injected")
                .isNotNull()
                .isInstanceOfAny(AlwaysPassStrategy.class, ExactMatchStrategy.class);

        var report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                strategy);

        assertThat(report)
                .hasEvaluationCount(2)
                .hasScoreGreaterThanOrEqualTo(0.0);
    }

    @StrategyTest(strategies = {
            QuarkusAlwaysPassStrategy.class,
            QuarkusExactMatchStrategy.class
    })
    void shouldSupportCDIBeanStrategies(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            EvaluationStrategy<String> strategy,
            Scorer scorer) {

        // Verify CDI bean strategies can be injected
        assertThat(strategy)
                .as("CDI bean strategy should be injected")
                .isNotNull()
                .isInstanceOfAny(QuarkusAlwaysPassStrategy.class, QuarkusExactMatchStrategy.class);

        var report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                strategy);

        assertThat(report)
                .hasAllPassed()
                .hasScoreGreaterThanOrEqualTo(100.0);
    }

    @StrategyTest(strategies = {
            AlwaysPassStrategy.class
    }, minScore = 90.0)
    void shouldWorkWithSingleStrategyAndMinScore(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            EvaluationStrategy<String> strategy,
            Scorer scorer) {

        assertThat(strategy).isInstanceOf(AlwaysPassStrategy.class);

        var report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                strategy);

        // AlwaysPassStrategy should meet the 90% minimum score
        assertThat(report)
                .hasAllPassed()
                .hasScoreGreaterThanOrEqualTo(90.0);
    }

    @StrategyTest(strategies = {
            ExactMatchStrategy.class,
            AlwaysPassStrategy.class
    })
    void shouldPreserveTestParametersAcrossInvocations(
            @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
            EvaluationStrategy<String> strategy,
            Scorer scorer) {

        // Verify all parameters are properly injected for each strategy invocation
        assertThat(samples)
                .as("Samples should be injected for each strategy")
                .isNotNull()
                .hasSize(2);

        assertThat(strategy)
                .as("Strategy should be injected for each invocation")
                .isNotNull();

        assertThat(scorer)
                .as("Scorer should be injected for each invocation")
                .isNotNull();

        // Run evaluation to verify everything works
        var report = scorer.evaluate(
                samples,
                params -> params.<String> get(0),
                strategy);

        assertThat(report).hasAllPassed();
    }
}
