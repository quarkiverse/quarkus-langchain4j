package io.quarkiverse.langchain4j.evaluation.junit5.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.ReportConfiguration;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.evaluation.junit5.StrategyTest;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationStrategy;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;

/**
 * Tests for @StrategyTest minScore validation.
 * <p>
 * These tests verify that minScore enforcement works correctly by using
 * the TestKit to run tests programmatically and verify their results.
 * </p>
 */
class StrategyTestMinScoreTest {

    @Test
    void shouldPassWhenScoreMeetsMinimum() {
        Events tests = EngineTestKit
                .engine("junit-jupiter")
                .selectors(org.junit.platform.engine.discovery.DiscoverySelectors
                        .selectClass(PassingMinScoreTest.class))
                .execute()
                .testEvents();

        tests.assertStatistics(stats -> stats
                .started(1) // One test invocation (AlwaysPassStrategy)
                .succeeded(1)
                .failed(0));
    }

    @Test
    void shouldFailWhenScoreBelowMinimum() {
        Events tests = EngineTestKit
                .engine("junit-jupiter")
                .selectors(org.junit.platform.engine.discovery.DiscoverySelectors
                        .selectClass(FailingMinScoreTest.class))
                .execute()
                .testEvents();

        tests.assertStatistics(stats -> stats
                .started(1) // One test invocation (AlwaysFailStrategy)
                .succeeded(0)
                .failed(1)); // Should fail due to score < minScore
    }

    @Test
    void shouldNotValidateWhenMinScoreIsZero() {
        Events tests = EngineTestKit
                .engine("junit-jupiter")
                .selectors(org.junit.platform.engine.discovery.DiscoverySelectors
                        .selectClass(NoMinScoreTest.class))
                .execute()
                .testEvents();

        tests.assertStatistics(stats -> stats
                .started(1) // One test invocation (AlwaysFailStrategy)
                .succeeded(1) // Should pass even with 0% score when minScore = 0
                .failed(0));
    }

    // Test classes used by the TestKit

    @ExtendWith(EvaluationExtension.class)
    static class PassingMinScoreTest {

        @ReportConfiguration
        private EvaluationReport<String> report;

        @StrategyTest(strategies = { AlwaysPassStrategy.class }, minScore = 90.0)
        void testWithPassingScore(
                @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
                EvaluationStrategy<String> strategy,
                Scorer scorer) {

            report = scorer.evaluate(
                    samples,
                    params -> params.<String> get(0),
                    strategy);

            // AlwaysPassStrategy returns 100% score, so should pass
            assertThat(report.score()).isGreaterThanOrEqualTo(90.0);
        }
    }

    @ExtendWith(EvaluationExtension.class)
    static class FailingMinScoreTest {

        @ReportConfiguration
        private EvaluationReport<String> report;

        @StrategyTest(strategies = { AlwaysFailStrategy.class }, minScore = 50.0)
        void testWithFailingScore(
                @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
                EvaluationStrategy<String> strategy,
                Scorer scorer) {

            report = scorer.evaluate(
                    samples,
                    params -> params.<String> get(0),
                    strategy);

            // AlwaysFailStrategy returns 0% score, so should fail automatic validation
        }
    }

    @ExtendWith(EvaluationExtension.class)
    static class NoMinScoreTest {

        @ReportConfiguration
        private EvaluationReport<String> report;

        @StrategyTest(strategies = { AlwaysFailStrategy.class }) // minScore = 0.0 by default
        void testWithoutMinScore(
                @SampleLocation("src/test/resources/smoke-tests.yaml") Samples<String> samples,
                EvaluationStrategy<String> strategy,
                Scorer scorer) {

            report = scorer.evaluate(
                    samples,
                    params -> params.<String> get(0),
                    strategy);

            // Should not fail even with 0% score when minScore is not specified
        }
    }
}
