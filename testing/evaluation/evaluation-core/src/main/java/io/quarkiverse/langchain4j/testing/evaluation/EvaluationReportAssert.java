package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.api.AbstractAssert;

/**
 * Fluent assertions for {@link EvaluationReport}.
 * <p>
 * This class provides rich assertion methods with detailed failure messages
 * that include scores, explanations, and sample details.
 * </p>
 */
public class EvaluationReportAssert extends AbstractAssert<EvaluationReportAssert, EvaluationReport<?>> {

    protected EvaluationReportAssert(EvaluationReport<?> actual) {
        super(actual, EvaluationReportAssert.class);
    }

    /**
     * Verifies that the evaluation report has a score greater than the specified threshold.
     *
     * @param threshold the minimum score threshold (0-100)
     * @return this assertion object for method chaining
     * @throws AssertionError if the score is not greater than the threshold
     */
    public EvaluationReportAssert hasScoreGreaterThan(double threshold) {
        isNotNull();

        if (actual.score() <= threshold) {
            failWithMessage(
                    "Expected evaluation score to be greater than %.2f%% but was %.2f%%\n\nFailed evaluations:\n%s",
                    threshold,
                    actual.score(),
                    formatFailures());
        }

        return this;
    }

    /**
     * Verifies that the evaluation report has a score greater than or equal to the specified threshold.
     *
     * @param threshold the minimum score threshold (0-100)
     * @return this assertion object for method chaining
     * @throws AssertionError if the score is less than the threshold
     */
    public EvaluationReportAssert hasScoreGreaterThanOrEqualTo(double threshold) {
        isNotNull();

        if (actual.score() < threshold) {
            failWithMessage(
                    "Expected evaluation score to be greater than or equal to %.2f%% but was %.2f%%\n\nFailed evaluations:\n%s",
                    threshold,
                    actual.score(),
                    formatFailures());
        }

        return this;
    }

    /**
     * Verifies that the evaluation report has a score less than the specified threshold.
     *
     * @param threshold the maximum score threshold (0-100)
     * @return this assertion object for method chaining
     * @throws AssertionError if the score is not less than the threshold
     */
    public EvaluationReportAssert hasScoreLessThan(double threshold) {
        isNotNull();

        if (actual.score() >= threshold) {
            failWithMessage(
                    "Expected evaluation score to be less than %.2f%% but was %.2f%%",
                    threshold,
                    actual.score());
        }

        return this;
    }

    /**
     * Verifies that the evaluation report has a score between the specified range (inclusive).
     *
     * @param min the minimum score (0-100)
     * @param max the maximum score (0-100)
     * @return this assertion object for method chaining
     * @throws AssertionError if the score is outside the range
     */
    public EvaluationReportAssert hasScoreBetween(double min, double max) {
        isNotNull();

        if (actual.score() < min || actual.score() > max) {
            failWithMessage(
                    "Expected evaluation score to be between %.2f%% and %.2f%% but was %.2f%%\n\nFailed evaluations:\n%s",
                    min,
                    max,
                    actual.score(),
                    formatFailures());
        }

        return this;
    }

    /**
     * Verifies that the evaluation report has exactly the specified score.
     *
     * @param expectedScore the expected score (0-100)
     * @return this assertion object for method chaining
     * @throws AssertionError if the score does not match
     */
    public EvaluationReportAssert hasScore(double expectedScore) {
        isNotNull();

        if (Math.abs(actual.score() - expectedScore) > 0.01) {
            failWithMessage(
                    "Expected evaluation score to be %.2f%% but was %.2f%%",
                    expectedScore,
                    actual.score());
        }

        return this;
    }

    /**
     * Verifies that all evaluations in the report passed.
     *
     * @return this assertion object for method chaining
     * @throws AssertionError if any evaluation failed
     */
    public EvaluationReportAssert hasAllPassed() {
        isNotNull();

        long failedCount = actual.evaluations().stream()
                .filter(e -> !e.passed())
                .count();

        if (failedCount > 0) {
            failWithMessage(
                    "Expected all evaluations to pass, but %d out of %d failed:\n%s",
                    failedCount,
                    actual.evaluations().size(),
                    formatFailures());
        }

        return this;
    }

    /**
     * Verifies that at least one evaluation in the report passed.
     *
     * @return this assertion object for method chaining
     * @throws AssertionError if all evaluations failed
     */
    public EvaluationReportAssert hasSomePassed() {
        isNotNull();

        long passedCount = actual.evaluations().stream()
                .filter(Scorer.EvaluationResult::passed)
                .count();

        if (passedCount == 0) {
            failWithMessage(
                    "Expected at least one evaluation to pass, but all %d evaluations failed:\n%s",
                    actual.evaluations().size(),
                    formatFailures());
        }

        return this;
    }

    /**
     * Verifies that all evaluations for a specific tag passed.
     *
     * @param tag the tag to check
     * @return this assertion object for method chaining
     * @throws AssertionError if any evaluation with the tag failed
     */
    public EvaluationReportAssert hasPassedForTag(String tag) {
        isNotNull();

        List<Scorer.EvaluationResult<?>> taggedEvaluations = actual.evaluations().stream()
                .filter(e -> e.sample().tags().contains(tag))
                .collect(Collectors.toList());

        if (taggedEvaluations.isEmpty()) {
            failWithMessage("No evaluations found with tag '%s'", tag);
        }

        long failedCount = taggedEvaluations.stream()
                .filter(e -> !e.passed())
                .count();

        if (failedCount > 0) {
            String failures = taggedEvaluations.stream()
                    .filter(e -> !e.passed())
                    .map(this::formatEvaluationFailure)
                    .collect(Collectors.joining("\n"));

            failWithMessage(
                    "Expected all evaluations with tag '%s' to pass, but %d out of %d failed:\n%s",
                    tag,
                    failedCount,
                    taggedEvaluations.size(),
                    failures);
        }

        return this;
    }

    /**
     * Verifies that evaluations for a specific tag have a score greater than or equal to the threshold.
     *
     * @param tag the tag to check
     * @param minScore the minimum score threshold (0-100)
     * @return this assertion object for method chaining
     * @throws AssertionError if the tag score is below the threshold
     */
    public EvaluationReportAssert hasScoreForTag(String tag, double minScore) {
        isNotNull();

        List<Scorer.EvaluationResult<?>> taggedEvaluations = actual.evaluations().stream()
                .filter(e -> e.sample().tags().contains(tag))
                .collect(Collectors.toList());

        if (taggedEvaluations.isEmpty()) {
            failWithMessage("No evaluations found with tag '%s'", tag);
        }

        double tagScore = actual.scoreForTag(tag);

        if (tagScore < minScore) {
            String failures = taggedEvaluations.stream()
                    .filter(e -> !e.passed())
                    .map(this::formatEvaluationFailure)
                    .collect(Collectors.joining("\n"));

            failWithMessage(
                    "Expected score for tag '%s' to be >= %.2f%% but was %.2f%%\n\nFailed evaluations:\n%s",
                    tag,
                    minScore,
                    tagScore,
                    failures);
        }

        return this;
    }

    /**
     * Verifies that the evaluation report has the specified number of evaluations.
     *
     * @param expectedCount the expected number of evaluations
     * @return this assertion object for method chaining
     * @throws AssertionError if the count does not match
     */
    public EvaluationReportAssert hasEvaluationCount(int expectedCount) {
        isNotNull();

        int actualCount = actual.evaluations().size();
        if (actualCount != expectedCount) {
            failWithMessage(
                    "Expected %d evaluations but found %d",
                    expectedCount,
                    actualCount);
        }

        return this;
    }

    /**
     * Verifies that the evaluation report has at least the specified number of evaluations.
     *
     * @param minCount the minimum number of evaluations
     * @return this assertion object for method chaining
     * @throws AssertionError if there are fewer evaluations
     */
    public EvaluationReportAssert hasAtLeastEvaluations(int minCount) {
        isNotNull();

        int actualCount = actual.evaluations().size();
        if (actualCount < minCount) {
            failWithMessage(
                    "Expected at least %d evaluations but found %d",
                    minCount,
                    actualCount);
        }

        return this;
    }

    /**
     * Verifies that the evaluation report has the specified number of passed evaluations.
     *
     * @param expectedCount the expected number of passed evaluations
     * @return this assertion object for method chaining
     * @throws AssertionError if the count does not match
     */
    public EvaluationReportAssert hasPassedCount(int expectedCount) {
        isNotNull();

        long actualCount = actual.evaluations().stream()
                .filter(Scorer.EvaluationResult::passed)
                .count();

        if (actualCount != expectedCount) {
            failWithMessage(
                    "Expected %d passed evaluations but found %d",
                    expectedCount,
                    actualCount);
        }

        return this;
    }

    /**
     * Verifies that the evaluation report has at least the specified number of passed evaluations.
     *
     * @param minCount the minimum number of passed evaluations
     * @return this assertion object for method chaining
     * @throws AssertionError if there are fewer passed evaluations
     */
    public EvaluationReportAssert hasAtLeastPassedEvaluations(int minCount) {
        isNotNull();

        long actualCount = actual.evaluations().stream()
                .filter(Scorer.EvaluationResult::passed)
                .count();

        if (actualCount < minCount) {
            failWithMessage(
                    "Expected at least %d passed evaluations but found %d",
                    minCount,
                    actualCount);
        }

        return this;
    }

    /**
     * Verifies that the evaluation report has the specified number of failed evaluations.
     *
     * @param expectedCount the expected number of failed evaluations
     * @return this assertion object for method chaining
     * @throws AssertionError if the count does not match
     */
    public EvaluationReportAssert hasFailedCount(int expectedCount) {
        isNotNull();

        long actualCount = actual.evaluations().stream()
                .filter(e -> !e.passed())
                .count();

        if (actualCount != expectedCount) {
            failWithMessage(
                    "Expected %d failed evaluations but found %d\n\nFailed evaluations:\n%s",
                    expectedCount,
                    actualCount,
                    formatFailures());
        }

        return this;
    }

    /**
     * Verifies that the evaluation report has at most the specified number of failed evaluations.
     *
     * @param maxCount the maximum number of failed evaluations
     * @return this assertion object for method chaining
     * @throws AssertionError if there are more failed evaluations
     */
    public EvaluationReportAssert hasAtMostFailedEvaluations(int maxCount) {
        isNotNull();

        long actualCount = actual.evaluations().stream()
                .filter(e -> !e.passed())
                .count();

        if (actualCount > maxCount) {
            failWithMessage(
                    "Expected at most %d failed evaluations but found %d\n\nFailed evaluations:\n%s",
                    maxCount,
                    actualCount,
                    formatFailures());
        }

        return this;
    }

    /**
     * Formats all failed evaluations as a detailed string.
     *
     * @return formatted string with failure details
     */
    private String formatFailures() {
        return actual.evaluations().stream()
                .filter(e -> !e.passed())
                .map(this::formatEvaluationFailure)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Formats a single evaluation failure with all details.
     *
     * @param evaluation the failed evaluation
     * @return formatted string with failure details
     */
    private String formatEvaluationFailure(Scorer.EvaluationResult<?> evaluation) {
        StringBuilder sb = new StringBuilder();
        sb.append("  - ").append(evaluation.sample().name()).append(":\n");
        sb.append("      expected = [").append(truncate(String.valueOf(evaluation.sample().expectedOutput()), 100))
                .append("]\n");
        sb.append("      actual   = [").append(truncate(String.valueOf(evaluation.result()), 100)).append("]\n");
        sb.append("      score    = ").append(String.format("%.2f", evaluation.score() * 100)).append("%\n");

        if (evaluation.explanation() != null && !evaluation.explanation().isEmpty()) {
            sb.append("      reason   = ").append(truncate(evaluation.explanation(), 200));
        }

        return sb.toString();
    }

    /**
     * Truncates a string to the specified length with ellipsis if needed.
     *
     * @param text the text to truncate
     * @param maxLength the maximum length
     * @return truncated string
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
