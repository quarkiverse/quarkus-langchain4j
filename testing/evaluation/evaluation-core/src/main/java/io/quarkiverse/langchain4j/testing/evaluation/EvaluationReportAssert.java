package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.AbstractAssert;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.testing.evaluation.review.Comparison;
import io.quarkiverse.langchain4j.testing.evaluation.review.ResultStatus;
import io.quarkiverse.langchain4j.testing.evaluation.review.Review;
import io.quarkiverse.langchain4j.testing.evaluation.review.SampleChange;

/**
 * Fluent assertions for {@link EvaluationReport}.
 * <p>
 * This class provides rich assertion methods with detailed failure messages
 * that include scores, explanations, and sample details.
 * </p>
 */
public class EvaluationReportAssert extends AbstractAssert<EvaluationReportAssert, EvaluationReport<?>> {

    private static final Logger LOG = Logger.getLogger(EvaluationReportAssert.class);
    private static final double DEFAULT_TOLERANCE = 5.0;

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

        long failedCount = blockingEvaluationResults()
                .filter(e -> !e.passed())
                .count();

        if (failedCount > 0) {
            failWithMessage(
                    "Expected all evaluations to pass, but %d out of %d failed:\n%s",
                    failedCount,
                    blockingEvaluationResults().count(),
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

        long passedCount = blockingEvaluationResults()
                .filter(Scorer.EvaluationResult::passed)
                .count();

        if (passedCount == 0) {
            failWithMessage(
                    "Expected at least one evaluation to pass, but all %d evaluations failed:\n%s",
                    blockingEvaluationResults().count(),
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

        List<Scorer.EvaluationResult<?>> taggedEvaluations = blockingEvaluationResults()
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

        List<Scorer.EvaluationResult<?>> taggedEvaluations = blockingEvaluationResults()
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
     * Verifies that the report is not worse than a baseline by more than a tolerance.
     * <p>
     * The comparison is paired per sample, see {@link EvaluationReport#compareWith(EvaluationSnapshot)}: the
     * assertion fails when the mean of the per-sample differences is below {@code -tolerance} points. Advisory
     * evaluations never fail it, but a drop of their mean per-sample score beyond the tolerance is logged as a
     * warning. Samples present in only one of the runs are ignored and logged. The failure message lists the
     * samples whose score dropped.
     * </p>
     *
     * @param baseline the snapshot of the previous run
     * @param tolerance how many points the mean per-sample score may drop, 0 or more
     * @return this assertion object for method chaining
     * @throws AssertionError if the report is worse than the baseline, or was produced with a different number of
     *         trials
     */
    public EvaluationReportAssert isNotWorseThan(EvaluationSnapshot baseline, double tolerance) {
        return isNotWorseThan(baseline, tolerance, "");
    }

    /**
     * Verifies that the report is not worse than the baseline kept in a store under the given id, see
     * {@link #isNotWorseThan(EvaluationSnapshot, double)}.
     * <p>
     * The report of the current run is always handed to the store with {@link BaselineStore#receive}, so it can
     * be accepted as the next baseline after the fact; failure messages say how. Without a stored baseline, or
     * when the baseline cannot be compared with this run, the assertion fails.
     * </p>
     *
     * @param store where baselines are kept
     * @param id the id of this evaluation in the store, for instance the test method name
     * @param tolerance how many points the mean per-sample score may drop, 0 or more
     * @return this assertion object for method chaining
     * @throws AssertionError if there is no baseline or the report is worse than it
     */
    public EvaluationReportAssert isNotWorseThan(BaselineStore store, String id, double tolerance) {
        return isNotWorseThanBaseline(store, Optional.of(id), tolerance);
    }

    /**
     * Verifies that the report is not worse than the committed baseline of the running test, with a tolerance of
     * 5 points. See {@link #isNotWorseThanBaseline(double)}.
     *
     * @return this assertion object for method chaining
     * @throws AssertionError if there is no baseline or the report is worse than it
     */
    public EvaluationReportAssert isNotWorseThanBaseline() {
        return isNotWorseThanBaseline(DEFAULT_TOLERANCE);
    }

    /**
     * Verifies that the report is not worse than the committed baseline of the running test.
     * <p>
     * The baseline is {@code src/test/resources/evaluation-baselines/<test class>/<test method>.json}. The result
     * of every run is written under the build directory, from where {@code quarkus langchain4j-eval review} can
     * accept it as the new baseline. See {@link #isNotWorseThan(EvaluationSnapshot, double)} for the comparison.
     * </p>
     *
     * @param tolerance how many points the mean per-sample score may drop, 0 or more
     * @return this assertion object for method chaining
     * @throws AssertionError if there is no baseline, the report is worse than it or cannot be compared with it, or
     *         the test cannot be told from the stack
     */
    public EvaluationReportAssert isNotWorseThanBaseline(double tolerance) {
        return isNotWorseThanBaseline(FileBaselineStore.conventional(), Optional.empty(), tolerance);
    }

    /**
     * Verifies that the report is not worse than the committed baseline with the given id, with a tolerance of 5
     * points. See {@link #isNotWorseThanBaseline(double)}.
     *
     * @param id the id of this evaluation, also its file name below the baselines directory
     * @return this assertion object for method chaining
     * @throws AssertionError if there is no baseline or the report is worse than it
     */
    public EvaluationReportAssert isNotWorseThanBaseline(String id) {
        return isNotWorseThanBaseline(id, DEFAULT_TOLERANCE);
    }

    /**
     * Verifies that the report is not worse than the committed baseline with the given id. See
     * {@link #isNotWorseThanBaseline(double)}.
     *
     * @param id the id of this evaluation, also its file name below the baselines directory
     * @param tolerance how many points the mean per-sample score may drop, 0 or more
     * @return this assertion object for method chaining
     * @throws AssertionError if there is no baseline or the report is worse than it
     */
    public EvaluationReportAssert isNotWorseThanBaseline(String id, double tolerance) {
        return isNotWorseThanBaseline(FileBaselineStore.conventional(), Optional.of(id), tolerance);
    }

    EvaluationReportAssert isNotWorseThanBaseline(BaselineStore store, Optional<String> id, double tolerance) {
        isNotNull();

        List<String> duplicates = actual.duplicateSampleNames();
        if (!duplicates.isEmpty()) {
            failWithMessage("Sample names must be unique, found duplicates: %s", String.join(", ", duplicates));
            return this;
        }
        Optional<String> resolved = id.or(TestIds::current);
        if (resolved.isEmpty()) {
            failWithMessage("Cannot tell which test is running: call isNotWorseThanBaseline from a JUnit test method, "
                    + "or pass an explicit id.");
            return this;
        }
        String testId = resolved.get();

        Optional<EvaluationSnapshot> loaded;
        Optional<BaselineComparison> comparison = Optional.empty();
        Optional<String> incompatibility = Optional.empty();
        boolean hasBaseline;
        try {
            loaded = store.load(testId);
            hasBaseline = loaded.isPresent();
        } catch (RuntimeException e) {
            // a corrupt baseline, for instance with merge conflict markers, is replaced by accepting this run
            loaded = Optional.empty();
            hasBaseline = true;
            incompatibility = Optional.of("the baseline file cannot be read: " + e.getMessage());
        }
        if (loaded.isPresent()) {
            try {
                comparison = Optional.of(actual.compareWith(loaded.get()));
                incompatibility = comparison.get().incompatibility();
            } catch (IllegalArgumentException e) {
                incompatibility = Optional.of(e.getMessage());
            }
        }

        String hint;
        try {
            hint = store.receive(testId, actual, comparisonOf(loaded, comparison, incompatibility, hasBaseline, tolerance));
        } catch (IllegalStateException e) {
            failWithMessage("%s", e.getMessage());
            return this;
        }
        Optional<String> errors = Review.errorSummary(actual.evaluations().stream()
                .filter(e -> e.thrown() != null).map(e -> JsonReportFormatter.describe(e.thrown())).toList(),
                actual.evaluations().size());
        if (errors.isPresent()) {
            // shown with any failure, and logged when the test passes, so an outage is not taken for a verdict
            LOG.warn(errors.get());
            hint = errors.get() + "\n\n" + hint;
        }

        if (!hasBaseline) {
            failWithMessage("No baseline yet for %s, %d samples pending review.\n\n%s", testId,
                    EvaluationSnapshot.of(actual).sampleNames().size(), hint);
            return this;
        }
        if (incompatibility.isPresent()) {
            failWithMessage("Cannot compare %s with its baseline: %s. Accept this run to make it the new baseline.\n\n%s",
                    testId, incompatibility.get(), hint);
            return this;
        }
        BaselineComparison compared = comparison.get();
        if (compared.differences().isEmpty()) {
            LOG.warnf("No sample of %s has blocking evaluations both in this run and in the baseline, so nothing was "
                    + "checked against the tolerance", testId);
        }
        if (!compared.added().isEmpty()) {
            LOG.infof("%d new samples pending review: %s", compared.added().size(), String.join(", ", compared.added()));
        }
        if (!compared.removed().isEmpty()) {
            LOG.infof("%d samples removed, pending review: %s", compared.removed().size(),
                    String.join(", ", compared.removed()));
        }
        checkRegression(compared, tolerance, "\n\n" + hint);
        return this;
    }

    private Comparison comparisonOf(Optional<EvaluationSnapshot> baseline, Optional<BaselineComparison> compared,
            Optional<String> incompatibility, boolean hasBaseline, double tolerance) {
        if (!hasBaseline) {
            return new Comparison(ResultStatus.NEW, tolerance, OptionalDouble.empty(), OptionalDouble.empty(),
                    OptionalDouble.empty(), OptionalDouble.empty(), List.of(),
                    EvaluationSnapshot.of(actual).sampleNames(), List.of(),
                    Optional.empty(), OptionalDouble.empty());
        }
        if (incompatibility.isPresent()) {
            return new Comparison(ResultStatus.INCOMPATIBLE, tolerance, OptionalDouble.empty(),
                    OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), List.of(), List.of(),
                    List.of(), incompatibility, OptionalDouble.empty());
        }
        BaselineComparison comparison = compared.get();
        EvaluationSnapshot before = baseline.get();
        EvaluationSnapshot now = EvaluationSnapshot.of(actual);
        List<SampleChange> samples = now.sampleNames().stream()
                .filter(name -> before.sampleNames().contains(name))
                .map(name -> new SampleChange(name, score(before.sampleScores(), name), score(now.sampleScores(), name),
                        score(before.advisorySampleScores(), name), score(now.advisorySampleScores(), name)))
                .toList();
        ResultStatus status;
        if (comparison.meanDifference() < -tolerance) {
            status = ResultStatus.FAILED;
        } else if (samples.stream().anyMatch(SampleChange::changed) || !comparison.added().isEmpty()
                || !comparison.removed().isEmpty()) {
            status = ResultStatus.CHANGED;
        } else {
            status = ResultStatus.UNCHANGED;
        }
        return new Comparison(status, tolerance, OptionalDouble.of(comparison.meanDifference()),
                OptionalDouble.of(comparison.standardError()),
                OptionalDouble.of(comparison.confidenceInterval().low()),
                OptionalDouble.of(comparison.confidenceInterval().high()), samples, comparison.added(),
                comparison.removed(), Optional.empty(), OptionalDouble.of(comparison.advisoryDifference()));
    }

    private static OptionalDouble score(Map<String, Double> scores, String name) {
        Double score = scores.get(name);
        return score == null ? OptionalDouble.empty() : OptionalDouble.of(score);
    }

    private EvaluationReportAssert isNotWorseThan(EvaluationSnapshot baseline, double tolerance, String trailer) {
        isNotNull();

        BaselineComparison comparison;
        try {
            comparison = actual.compareWith(baseline);
        } catch (IllegalArgumentException e) {
            failWithMessage("Cannot compare with the baseline: %s%s", e.getMessage(), trailer);
            return this;
        }
        if (comparison.incompatibility().isPresent()) {
            failWithMessage("Cannot compare with the baseline: %s%s", comparison.incompatibility().get(), trailer);
            return this;
        }

        if (!comparison.added().isEmpty()) {
            LOG.infof("%d sample(s) are not in the baseline and were not compared: %s", comparison.added().size(),
                    String.join(", ", comparison.added()));
        }
        if (!comparison.removed().isEmpty()) {
            LOG.infof("%d sample(s) are only in the baseline and were not compared: %s", comparison.removed().size(),
                    String.join(", ", comparison.removed()));
        }

        checkRegression(comparison, tolerance, trailer);
        return this;
    }

    private void checkRegression(BaselineComparison comparison, double tolerance, String trailer) {
        if (comparison.advisoryDifference() < -tolerance) {
            LOG.warnf("The advisory evaluations dropped by %.2f points per sample against the baseline. They do not "
                    + "fail the test, but are worth a look in the report.", -comparison.advisoryDifference());
        }

        if (comparison.meanDifference() < -tolerance) {
            StringBuilder regressions = new StringBuilder();
            for (BaselineComparison.SampleDifference regression : comparison.regressions()) {
                regressions.append(String.format("  - %s: %.2f%% -> %.2f%%\n", regression.name(),
                        regression.baselineScore(), regression.currentScore()));
            }
            failWithMessage(
                    "Expected the score not to drop by more than %.2f points against the baseline, "
                            + "but the mean per-sample difference is %.2f points (95%% CI: %.2f to %.2f) "
                            + "over %d paired samples\n\nRegressed samples:\n%s%s",
                    tolerance,
                    comparison.meanDifference(),
                    comparison.confidenceInterval().low(),
                    comparison.confidenceInterval().high(),
                    comparison.differences().size(),
                    regressions,
                    trailer);
        }

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

        long actualCount = blockingEvaluationResults().count();
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

        long actualCount = blockingEvaluationResults().count();
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

        long actualCount = blockingEvaluationResults()
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

        long actualCount = blockingEvaluationResults()
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

        long actualCount = blockingEvaluationResults()
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

        long actualCount = blockingEvaluationResults()
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
     * The evaluations that count: those not produced by an advisory strategy. No assertion of this class looks at
     * advisory evaluations, so {@code passed + failed == evaluationCount} always holds.
     */
    private Stream<? extends Scorer.EvaluationResult<?>> blockingEvaluationResults() {
        return actual.evaluations().stream().filter(e -> !e.advisory());
    }

    /**
     * Formats all failed evaluations as a detailed string.
     *
     * @return formatted string with failure details
     */
    private String formatFailures() {
        return blockingEvaluationResults()
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
