package io.quarkiverse.langchain4j.evaluation.junit5.it;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationDisplayNameGenerator;
import io.quarkiverse.langchain4j.evaluation.junit5.EvaluationExtension;
import io.quarkiverse.langchain4j.evaluation.junit5.SampleLocation;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.EvaluationResult;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;
import io.quarkiverse.langchain4j.testing.evaluation.Scorer;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test verifying that EvaluationAssertions work correctly
 * in a real JUnit 5 test environment with Quarkus and CDI.
 */
@QuarkusTest
@ExtendWith(EvaluationExtension.class)
@DisplayNameGeneration(EvaluationDisplayNameGenerator.class)
class EvaluationAssertionsIT {

    @Test
    void shouldPassWhenAllEvaluationsPass(
            Scorer scorer,
            @SampleLocation("src/test/resources/assertion-test-samples.yaml") Samples<String> samples) {
        EvaluationReport<String> report = scorer.evaluate(samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        // Should pass - all samples in assertion-test-samples.yaml should match
        assertThat(report)
                .hasAllPassed()
                .hasScoreGreaterThanOrEqualTo(90.0)
                .hasEvaluationCount(3)
                .hasPassedCount(3)
                .hasFailedCount(0);
    }

    @Test
    void shouldProvideRichFailureMessagesWithScoresAndExplanations(
            Scorer scorer,
            @SampleLocation("src/test/resources/assertion-test-samples.yaml") Samples<String> samples) {
        // Create a scenario where some evaluations fail
        EvaluationReport<String> report = scorer.evaluate(samples,
                params -> "wrong output", // Intentionally wrong
                (sample, actual) -> {
                    boolean passed = sample.expectedOutput().equals(actual);
                    if (!passed) {
                        double similarity = calculateSimilarity(sample.expectedOutput(), actual);
                        String explanation = String.format(
                                "Output mismatch: similarity %.2f below threshold 1.00",
                                similarity);
                        return new EvaluationResult(false, similarity, explanation, java.util.Map.of());
                    }
                    return EvaluationResult.fromBoolean(true);
                });

        // Verify that assertion failures include rich details
        assertThatThrownBy(() -> assertThat(report).hasAllPassed())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected all evaluations to pass")
                .hasMessageContaining("expected =")
                .hasMessageContaining("actual   =")
                .hasMessageContaining("score    =")
                .hasMessageContaining("reason   =");
    }

    @Test
    void shouldSupportTagBasedAssertions(
            Scorer scorer,
            @SampleLocation("src/test/resources/assertion-test-samples.yaml") Samples<String> samples) {
        // Evaluate with all passing
        EvaluationReport<String> report = scorer.evaluate(samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        // Test tag-based assertions (test-samples.yaml has tags)
        assertThat(report)
                .hasPassedForTag("greeting")
                .hasScoreForTag("greeting", 95.0);
    }

    @Test
    void shouldSupportMethodChaining(
            Scorer scorer,
            @SampleLocation("src/test/resources/assertion-test-samples.yaml") Samples<String> samples) {
        EvaluationReport<String> report = scorer.evaluate(samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        // Verify method chaining works in real scenario
        assertThat(report)
                .hasScoreGreaterThanOrEqualTo(90.0)
                .hasAllPassed()
                .hasEvaluationCount(3)
                .hasAtLeastEvaluations(1)
                .hasPassedCount(3)
                .hasFailedCount(0)
                .hasScoreBetween(95.0, 100.0);
    }

    @Test
    void shouldHandlePartialFailures(
            Scorer scorer,
            @SampleLocation("src/test/resources/assertion-test-samples.yaml") Samples<String> samples) {
        // Create a scenario where only some evaluations pass
        final int[] counter = { 0 };
        EvaluationReport<String> report = scorer.evaluate(samples,
                params -> {
                    counter[0]++;
                    // First sample passes, rest fail
                    return counter[0] == 1 ? params.<String> get(0) : "wrong";
                },
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        // Verify assertions work with partial failures
        assertThat(report)
                .hasSomePassed()
                .hasPassedCount(1)
                .hasFailedCount(2)
                .hasScoreBetween(30.0, 40.0);

        // Verify hasAllPassed fails with detailed info
        assertThatThrownBy(() -> assertThat(report).hasAllPassed())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("2 out of 3 failed");
    }

    @Test
    void shouldWorkWithCustomEvaluationStrategies(
            Scorer scorer,
            @SampleLocation("src/test/resources/assertion-test-samples.yaml") Samples<String> samples) {
        // Use a similarity-based strategy that returns scores
        EvaluationReport<String> report = scorer.evaluate(samples,
                params -> params.<String> get(0).toLowerCase(), // Lowercase transformation
                (sample, actual) -> {
                    String expected = sample.expectedOutput().toLowerCase();
                    double similarity = calculateSimilarity(expected, actual);
                    return new EvaluationResult(
                            similarity >= 0.95,
                            similarity,
                            similarity < 0.95 ? "Similarity too low" : null,
                            java.util.Map.of("expected_lower", expected, "actual_lower", actual));
                });

        // Should pass since we're comparing lowercase versions
        assertThat(report)
                .hasScoreGreaterThanOrEqualTo(95.0)
                .hasAllPassed();
    }

    @Test
    void shouldProvideDetailedCountMismatchMessages(
            Scorer scorer,
            @SampleLocation("src/test/resources/assertion-test-samples.yaml") Samples<String> samples) {
        EvaluationReport<String> report = scorer.evaluate(samples,
                params -> params.<String> get(0),
                (sample, actual) -> EvaluationResult.fromBoolean(
                        sample.expectedOutput().equals(actual)));

        // Test count assertions with wrong expectations
        assertThatThrownBy(() -> assertThat(report).hasEvaluationCount(5))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected 5 evaluations but found 3");

        assertThatThrownBy(() -> assertThat(report).hasAtLeastEvaluations(10))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected at least 10 evaluations but found 3");
    }

    /**
     * Simple similarity calculation for testing purposes.
     * Returns 1.0 for identical strings, 0.0 for completely different strings.
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - ((double) distance / maxLen);
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }

        return dp[s1.length()][s2.length()];
    }
}
