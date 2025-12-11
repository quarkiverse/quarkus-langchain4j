package io.quarkiverse.langchain4j.testing.evaluation;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class EvaluationAssertionsTest {

    @Test
    void hasScoreGreaterThan_shouldPassWhenScoreIsAboveThreshold() {
        EvaluationReport<String> report = createReportWithScore(90.0);

        assertThat(report).hasScoreGreaterThan(85.0);
    }

    @Test
    void hasScoreGreaterThan_shouldFailWhenScoreIsEqualToThreshold() {
        EvaluationReport<String> report = createReportWithScore(85.0);

        assertThatThrownBy(() -> assertThat(report).hasScoreGreaterThan(85.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected evaluation score to be greater than 85.00%")
                .hasMessageContaining("but was 85.00%");
    }

    @Test
    void hasScoreGreaterThan_shouldFailWhenScoreisBelowThreshold() {
        EvaluationReport<String> report = createReportWithScore(75.0);

        assertThatThrownBy(() -> assertThat(report).hasScoreGreaterThan(85.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected evaluation score to be greater than 85.00%")
                .hasMessageContaining("but was 75.00%")
                .hasMessageContaining("Failed evaluations:");
    }

    @Test
    void hasScoreGreaterThanOrEqualTo_shouldPassWhenScoreIsEqual() {
        EvaluationReport<String> report = createReportWithScore(85.0);

        assertThat(report).hasScoreGreaterThanOrEqualTo(85.0);
    }

    @Test
    void hasScoreGreaterThanOrEqualTo_shouldPassWhenScoreIsAbove() {
        EvaluationReport<String> report = createReportWithScore(90.0);

        assertThat(report).hasScoreGreaterThanOrEqualTo(85.0);
    }

    @Test
    void hasScoreGreaterThanOrEqualTo_shouldFailWhenScoreIsBelow() {
        EvaluationReport<String> report = createReportWithScore(80.0);

        assertThatThrownBy(() -> assertThat(report).hasScoreGreaterThanOrEqualTo(85.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected evaluation score to be greater than or equal to 85.00%")
                .hasMessageContaining("but was 80.00%");
    }

    @Test
    void hasScoreLessThan_shouldPassWhenScoreIsBelow() {
        EvaluationReport<String> report = createReportWithScore(75.0);

        assertThat(report).hasScoreLessThan(80.0);
    }

    @Test
    void hasScoreLessThan_shouldFailWhenScoreIsEqual() {
        EvaluationReport<String> report = createReportWithScore(80.0);

        assertThatThrownBy(() -> assertThat(report).hasScoreLessThan(80.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected evaluation score to be less than 80.00%");
    }

    @Test
    void hasScoreBetween_shouldPassWhenScoreIsInRange() {
        EvaluationReport<String> report = createReportWithScore(85.0);

        assertThat(report).hasScoreBetween(80.0, 90.0);
    }

    @Test
    void hasScoreBetween_shouldPassWhenScoreIsAtBoundary() {
        EvaluationReport<String> report = createReportWithScore(80.0);

        assertThat(report).hasScoreBetween(80.0, 90.0);

        report = createReportWithScore(90.0);
        assertThat(report).hasScoreBetween(80.0, 90.0);
    }

    @Test
    void hasScoreBetween_shouldFailWhenScoreIsBelowRange() {
        EvaluationReport<String> report = createReportWithScore(75.0);

        assertThatThrownBy(() -> assertThat(report).hasScoreBetween(80.0, 90.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected evaluation score to be between 80.00% and 90.00%")
                .hasMessageContaining("but was 75.00%");
    }

    @Test
    void hasScoreBetween_shouldFailWhenScoreIsAboveRange() {
        EvaluationReport<String> report = createReportWithScore(95.0);

        assertThatThrownBy(() -> assertThat(report).hasScoreBetween(80.0, 90.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected evaluation score to be between 80.00% and 90.00%")
                .hasMessageContaining("but was 95.00%");
    }

    @Test
    void hasScore_shouldPassWhenScoreMatches() {
        EvaluationReport<String> report = createReportWithScore(85.0);

        assertThat(report).hasScore(85.0);
    }

    @Test
    void hasScore_shouldFailWhenScoreDoesNotMatch() {
        EvaluationReport<String> report = createReportWithScore(85.0);

        assertThatThrownBy(() -> assertThat(report).hasScore(90.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected evaluation score to be 90.00%")
                .hasMessageContaining("but was 85.00%");
    }

    @Test
    void hasAllPassed_shouldPassWhenAllEvaluationsPass() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createPassedResult("Sample2", "output2"));

        assertThat(report).hasAllPassed();
    }

    @Test
    void hasAllPassed_shouldFailWhenSomeEvaluationsFail() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createFailedResult("Sample2", "expected2", "actual2", "Too different"));

        assertThatThrownBy(() -> assertThat(report).hasAllPassed())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected all evaluations to pass, but 1 out of 2 failed")
                .hasMessageContaining("Sample2")
                .hasMessageContaining("expected = [expected2]")
                .hasMessageContaining("actual   = [actual2]")
                .hasMessageContaining("reason   = Too different");
    }

    @Test
    void hasSomePassed_shouldPassWhenAtLeastOneEvaluationPasses() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createFailedResult("Sample2", "expected2", "actual2", null));

        assertThat(report).hasSomePassed();
    }

    @Test
    void hasSomePassed_shouldFailWhenAllEvaluationsFail() {
        EvaluationReport<String> report = createReportWithResults(
                createFailedResult("Sample1", "expected1", "actual1", null),
                createFailedResult("Sample2", "expected2", "actual2", null));

        assertThatThrownBy(() -> assertThat(report).hasSomePassed())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected at least one evaluation to pass, but all 2 evaluations failed");
    }

    @Test
    void hasPassedForTag_shouldPassWhenAllTaggedEvaluationsPass() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1", "tag1"),
                createPassedResult("Sample2", "output2", "tag1"),
                createFailedResult("Sample3", "expected3", "actual3", null, "tag2"));

        assertThat(report).hasPassedForTag("tag1");
    }

    @Test
    void hasPassedForTag_shouldFailWhenSomeTaggedEvaluationsFail() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1", "tag1"),
                createFailedResult("Sample2", "expected2", "actual2", "Mismatch", "tag1"));

        assertThatThrownBy(() -> assertThat(report).hasPassedForTag("tag1"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected all evaluations with tag 'tag1' to pass, but 1 out of 2 failed")
                .hasMessageContaining("Sample2");
    }

    @Test
    void hasPassedForTag_shouldFailWhenTagNotFound() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1", "tag1"));

        assertThatThrownBy(() -> assertThat(report).hasPassedForTag("nonexistent"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No evaluations found with tag 'nonexistent'");
    }

    @Test
    void hasScoreForTag_shouldPassWhenTagScoreIsAboveThreshold() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1", "tag1"),
                createPassedResult("Sample2", "output2", "tag1"));

        assertThat(report).hasScoreForTag("tag1", 90.0);
    }

    @Test
    void hasScoreForTag_shouldFailWhenTagScoreIsBelowThreshold() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1", "tag1"),
                createFailedResult("Sample2", "expected2", "actual2", null, "tag1"));

        assertThatThrownBy(() -> assertThat(report).hasScoreForTag("tag1", 90.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected score for tag 'tag1' to be >= 90.00% but was 50.00%")
                .hasMessageContaining("Sample2");
    }

    @Test
    void hasScoreForTag_shouldFailWhenTagNotFound() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1", "tag1"));

        assertThatThrownBy(() -> assertThat(report).hasScoreForTag("nonexistent", 50.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No evaluations found with tag 'nonexistent'");
    }

    @Test
    void hasEvaluationCount_shouldPassWhenCountMatches() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createPassedResult("Sample2", "output2"));

        assertThat(report).hasEvaluationCount(2);
    }

    @Test
    void hasEvaluationCount_shouldFailWhenCountDoesNotMatch() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"));

        assertThatThrownBy(() -> assertThat(report).hasEvaluationCount(2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected 2 evaluations but found 1");
    }

    @Test
    void hasAtLeastEvaluations_shouldPassWhenCountIsSufficient() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createPassedResult("Sample2", "output2"),
                createPassedResult("Sample3", "output3"));

        assertThat(report).hasAtLeastEvaluations(2);
    }

    @Test
    void hasAtLeastEvaluations_shouldFailWhenCountIsInsufficient() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"));

        assertThatThrownBy(() -> assertThat(report).hasAtLeastEvaluations(2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected at least 2 evaluations but found 1");
    }

    @Test
    void hasPassedCount_shouldPassWhenCountMatches() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createPassedResult("Sample2", "output2"),
                createFailedResult("Sample3", "expected3", "actual3", null));

        assertThat(report).hasPassedCount(2);
    }

    @Test
    void hasPassedCount_shouldFailWhenCountDoesNotMatch() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"));

        assertThatThrownBy(() -> assertThat(report).hasPassedCount(2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected 2 passed evaluations but found 1");
    }

    @Test
    void hasFailedCount_shouldPassWhenCountMatches() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createFailedResult("Sample2", "expected2", "actual2", null),
                createFailedResult("Sample3", "expected3", "actual3", null));

        assertThat(report).hasFailedCount(2);
    }

    @Test
    void hasFailedCount_shouldFailWhenCountDoesNotMatch() {
        EvaluationReport<String> report = createReportWithResults(
                createFailedResult("Sample1", "expected1", "actual1", null));

        assertThatThrownBy(() -> assertThat(report).hasFailedCount(2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected 2 failed evaluations but found 1")
                .hasMessageContaining("Sample1");
    }

    @Test
    void hasAtLeastPassedEvaluations_shouldPassWhenCountIsSufficient() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createPassedResult("Sample2", "output2"),
                createPassedResult("Sample3", "output3"),
                createFailedResult("Sample4", "expected4", "actual4", null));

        assertThat(report).hasAtLeastPassedEvaluations(3);
        assertThat(report).hasAtLeastPassedEvaluations(2);
        assertThat(report).hasAtLeastPassedEvaluations(1);
    }

    @Test
    void hasAtLeastPassedEvaluations_shouldPassWhenCountIsExact() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createPassedResult("Sample2", "output2"));

        assertThat(report).hasAtLeastPassedEvaluations(2);
    }

    @Test
    void hasAtLeastPassedEvaluations_shouldFailWhenCountIsInsufficient() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createFailedResult("Sample2", "expected2", "actual2", null));

        assertThatThrownBy(() -> assertThat(report).hasAtLeastPassedEvaluations(2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected at least 2 passed evaluations but found 1");
    }

    @Test
    void hasAtMostFailedEvaluations_shouldPassWhenCountIsBelowLimit() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createPassedResult("Sample2", "output2"),
                createFailedResult("Sample3", "expected3", "actual3", null),
                createFailedResult("Sample4", "expected4", "actual4", null));

        assertThat(report).hasAtMostFailedEvaluations(3);
        assertThat(report).hasAtMostFailedEvaluations(2);
    }

    @Test
    void hasAtMostFailedEvaluations_shouldPassWhenCountIsExact() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1"),
                createFailedResult("Sample2", "expected2", "actual2", null),
                createFailedResult("Sample3", "expected3", "actual3", null));

        assertThat(report).hasAtMostFailedEvaluations(2);
    }

    @Test
    void hasAtMostFailedEvaluations_shouldFailWhenCountExceedsLimit() {
        EvaluationReport<String> report = createReportWithResults(
                createFailedResult("Sample1", "expected1", "actual1", null),
                createFailedResult("Sample2", "expected2", "actual2", null),
                createFailedResult("Sample3", "expected3", "actual3", null));

        assertThatThrownBy(() -> assertThat(report).hasAtMostFailedEvaluations(2))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected at most 2 failed evaluations but found 3")
                .hasMessageContaining("Sample1")
                .hasMessageContaining("Sample2")
                .hasMessageContaining("Sample3");
    }

    @Test
    void assertions_shouldChainMultipleMethods() {
        EvaluationReport<String> report = createReportWithResults(
                createPassedResult("Sample1", "output1", "critical"),
                createPassedResult("Sample2", "output2", "critical"),
                createPassedResult("Sample3", "output3", "optional"));

        assertThat(report)
                .hasScoreGreaterThanOrEqualTo(90.0)
                .hasAllPassed()
                .hasEvaluationCount(3)
                .hasPassedCount(3)
                .hasFailedCount(0)
                .hasPassedForTag("critical")
                .hasScoreForTag("critical", 95.0);
    }

    @Test
    void assertions_shouldIncludeExplanationInFailureMessage() {
        EvaluationReport<String> report = createReportWithResults(
                createFailedResult("Sample1", "The cat sat on the mat",
                        "The dog sat on the mat", "Similarity 0.87 below threshold 0.90"));

        assertThatThrownBy(() -> assertThat(report).hasAllPassed())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Sample1")
                .hasMessageContaining("expected = [The cat sat on the mat]")
                .hasMessageContaining("actual   = [The dog sat on the mat]")
                .hasMessageContaining("reason   = Similarity 0.87 below threshold 0.90");
    }

    @Test
    void assertions_shouldTruncateLongOutputs() {
        String longText = "a".repeat(200);
        EvaluationReport<String> report = createReportWithResults(
                createFailedResult("Sample1", longText, longText + "x", null));

        assertThatThrownBy(() -> assertThat(report).hasAllPassed())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Sample1")
                .hasMessageContaining("...");
    }

    // Helper methods to create test data

    private EvaluationReport<String> createReportWithScore(double score) {
        int total = 100;
        int passed = (int) (total * score / 100.0);

        Scorer.EvaluationResult<String>[] results = new Scorer.EvaluationResult[total];
        for (int i = 0; i < passed; i++) {
            results[i] = createPassedResult("Sample" + i, "output" + i);
        }
        for (int i = passed; i < total; i++) {
            results[i] = createFailedResult("Sample" + i, "expected" + i, "actual" + i, null);
        }

        return new EvaluationReport<>(List.of(results));
    }

    private EvaluationReport<String> createReportWithResults(Scorer.EvaluationResult<String>... results) {
        return new EvaluationReport<>(List.of(results));
    }

    private Scorer.EvaluationResult<String> createPassedResult(String name, String output, String... tags) {
        EvaluationSample<String> sample = new EvaluationSample<>(name, new Parameters(), output, List.of(tags));
        return Scorer.EvaluationResult.fromCompletedEvaluation(
                sample,
                output,
                EvaluationResult.fromBoolean(true));
    }

    private Scorer.EvaluationResult<String> createFailedResult(String name, String expected, String actual,
            String explanation, String... tags) {
        EvaluationSample<String> sample = new EvaluationSample<>(name, new Parameters(), expected, List.of(tags));
        return Scorer.EvaluationResult.fromCompletedEvaluation(
                sample,
                actual,
                explanation != null
                        ? new EvaluationResult(false, 0.0, explanation, java.util.Map.of())
                        : EvaluationResult.fromBoolean(false));
    }
}
