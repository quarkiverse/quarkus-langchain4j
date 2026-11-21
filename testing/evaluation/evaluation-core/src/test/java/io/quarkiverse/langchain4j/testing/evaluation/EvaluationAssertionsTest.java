package io.quarkiverse.langchain4j.testing.evaluation;

import static io.quarkiverse.langchain4j.testing.evaluation.EvaluationAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.langchain4j.testing.evaluation.review.PendingFile;
import io.quarkiverse.langchain4j.testing.evaluation.review.PendingResult;
import io.quarkiverse.langchain4j.testing.evaluation.review.PendingResults;
import io.quarkiverse.langchain4j.testing.evaluation.review.Review;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    void advisoryFailuresShouldNotFailAssertions() {
        EvaluationSample<String> sample = new EvaluationSample<>("S", new Parameters(), "expected", List.of("tag"));
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample, "expected", EvaluationResult.fromBoolean(true)),
                Scorer.EvaluationResult.fromCompletedEvaluation(sample, "expected", EvaluationResult.failed("judge"), true)));

        assertThat(report)
                .hasAllPassed()
                .hasPassedForTag("tag")
                .hasScoreForTag("tag", 100.0)
                .hasPassedCount(1)
                .hasAtLeastPassedEvaluations(1)
                .hasFailedCount(0)
                .hasAtMostFailedEvaluations(0)
                .hasEvaluationCount(1)
                .hasAtLeastEvaluations(1);
        assertThatThrownBy(() -> assertThat(report).hasEvaluationCount(2))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> assertThat(report).hasAtLeastEvaluations(2))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void advisoryPassesShouldNotSatisfyAssertions() {
        EvaluationSample<String> sample = new EvaluationSample<>("S", new Parameters(), "expected", List.of("tag"));
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample, "other", EvaluationResult.fromBoolean(false)),
                Scorer.EvaluationResult.fromCompletedEvaluation(sample, "other", EvaluationResult.fromBoolean(true), true)));

        assertThatThrownBy(() -> assertThat(report).hasSomePassed())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("all 1 evaluations failed");
        assertThatThrownBy(() -> assertThat(report).hasAllPassed())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("1 out of 1 failed");
    }

    @Test
    void isNotWorseThan_shouldPassWithinTolerance() {
        EvaluationSnapshot baseline = new EvaluationSnapshot(1, List.of(), List.of(
                new EvaluationSnapshot.Record("A", List.of(), true, false),
                new EvaluationSnapshot.Record("B", List.of(), true, false)));
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("A"), "x", EvaluationResult.fromBoolean(true)),
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("B"), "x", EvaluationResult.fromBoolean(true))));

        assertThat(report).isNotWorseThan(baseline, 0.0);
    }

    @Test
    void isNotWorseThan_shouldFailAndNameTheRegressedSamples() {
        EvaluationSnapshot baseline = new EvaluationSnapshot(1, List.of(), List.of(
                new EvaluationSnapshot.Record("A", List.of(), true, false),
                new EvaluationSnapshot.Record("B", List.of(), true, false)));
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("A"), "x", EvaluationResult.fromBoolean(true)),
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("B"), "x", EvaluationResult.fromBoolean(false))));

        assertThatThrownBy(() -> assertThat(report).isNotWorseThan(baseline, 10.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("-50.00")
                .hasMessageContaining("10.00")
                .hasMessageContaining("B: 100.00% -> 0.00%")
                .hasMessageNotContaining("A:");
    }

    @Test
    void isNotWorseThan_shouldFailWhenTrialsDiffer() {
        EvaluationSnapshot baseline = new EvaluationSnapshot(3, List.of(), List.of(
                new EvaluationSnapshot.Record("A", List.of(), true, false)));
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("A"), "x", EvaluationResult.fromBoolean(true))));

        assertThatThrownBy(() -> assertThat(report).isNotWorseThan(baseline, 0.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("trials");
    }

    @Test
    void isNotWorseThan_withStoreShouldFailWhenTheBaselineIsMissingAndSayHowToReviewTheReceivedReport(
            @TempDir Path tempDir) {
        FileBaselineStore store = new FileBaselineStore(tempDir.resolve("baselines"), tempDir.resolve("received"));
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("A"), "x", EvaluationResult.fromBoolean(true))));

        assertThatThrownBy(() -> assertThat(report).isNotWorseThan(store, "faq", 0.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No baseline yet for faq, 1 samples pending review.")
                .hasMessageContaining(reviewHint(store, "faq"));
        org.assertj.core.api.Assertions.assertThat(store.load("faq")).isEmpty();
        org.assertj.core.api.Assertions.assertThat(store.pendingFileOf("faq")).exists();
    }

    @Test
    void isNotWorseThan_withStoreShouldWriteThePendingResultOnEveryRun(@TempDir Path tempDir) {
        FileBaselineStore store = new FileBaselineStore(tempDir.resolve("baselines"), tempDir.resolve("received"));
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("A"), "x", EvaluationResult.fromBoolean(true))));
        store.save("faq", report);

        assertThat(report).isNotWorseThan(store, "faq", 0.0);

        org.assertj.core.api.Assertions.assertThat(store.pendingFileOf("faq")).exists();
    }

    @Test
    void isNotWorseThan_withStoreShouldCompareWithTheStoredBaseline(@TempDir Path tempDir) {
        FileBaselineStore store = new FileBaselineStore(tempDir.resolve("baselines"), tempDir.resolve("received"));
        store.save("faq", new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("A"), "x", EvaluationResult.fromBoolean(true)))));
        EvaluationReport<String> worse = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("A"), "x", EvaluationResult.fromBoolean(false))));

        assertThatThrownBy(() -> assertThat(worse).isNotWorseThan(store, "faq", 0.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("A: 100.00% -> 0.00%")
                .hasMessageContaining("Run `quarkus langchain4j-eval review` to accept or reject it.");
    }

    @Test
    void isNotWorseThan_shouldWarnWhenAdvisoryEvaluationsDropEvenIfItPasses() {
        EvaluationSnapshot baseline = new EvaluationSnapshot(1, List.of(), List.of(
                new EvaluationSnapshot.Record("A", List.of(), true, false),
                new EvaluationSnapshot.Record("A", List.of(), true, true)));
        EvaluationSample<String> a = sample("A");
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(a, "x", EvaluationResult.fromBoolean(true)),
                Scorer.EvaluationResult.fromCompletedEvaluation(a, "x", EvaluationResult.fromBoolean(false),
                        true)));
        List<String> warnings = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(EvaluationReportAssert.class.getName());
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                    warnings.add(new java.util.logging.SimpleFormatter().formatMessage(record));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            assertThat(report).isNotWorseThan(baseline, 5.0);
        } finally {
            logger.removeHandler(handler);
        }

        org.assertj.core.api.Assertions.assertThat(warnings).singleElement().asString()
                .contains("advisory")
                .contains("dropped by 100.00 points");
    }

    @Test
    void isNotWorseThan_shouldNotWarnWhenAdvisoryEvaluationsStayWithinTolerance() {
        EvaluationSnapshot baseline = new EvaluationSnapshot(1, List.of(), List.of(
                new EvaluationSnapshot.Record("A", List.of(), true, false),
                new EvaluationSnapshot.Record("A", List.of(), true, true)));
        EvaluationSample<String> a = sample("A");
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(a, "x", EvaluationResult.fromBoolean(true)),
                Scorer.EvaluationResult.fromCompletedEvaluation(a, "x", EvaluationResult.fromBoolean(true),
                        true)));
        List<String> warnings = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(EvaluationReportAssert.class.getName());
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                if (record.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) {
                    warnings.add(record.getMessage());
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            assertThat(report).isNotWorseThan(baseline, 5.0);
        } finally {
            logger.removeHandler(handler);
        }

        org.assertj.core.api.Assertions.assertThat(warnings).isEmpty();
    }

    private static String reviewHint(FileBaselineStore store, String id) {
        return "Pending result: " + store.pendingFileOf(id) + "\nBaseline: " + store.fileOf(id)
                + "\nRun `quarkus langchain4j-eval review` to accept or reject it.\n"
                + "Manual review (no CLI): https://docs.quarkiverse.io/quarkus-langchain4j/dev/testing.html#reviewing-results-manually";
    }

    private static String idOf(String method) {
        return EvaluationAssertionsTest.class.getName() + "/" + method;
    }

    private static EvaluationReport<String> reportOf(boolean... passed) {
        List<Scorer.EvaluationResult<String>> results = new java.util.ArrayList<>();
        for (int i = 0; i < passed.length; i++) {
            results.add(Scorer.EvaluationResult.fromCompletedEvaluation(sample(String.valueOf((char) ('A' + i))), "x",
                    EvaluationResult.fromBoolean(passed[i])));
        }
        return new EvaluationReport<>(results);
    }

    private static JsonNode pendingComparison(FileBaselineStore store, String id) throws IOException {
        return new ObjectMapper().readTree(store.pendingFileOf(id).toFile()).path(PendingFile.COMPARISON);
    }

    private static void assertStatus(FileBaselineStore store, String id, String status) throws IOException {
        org.assertj.core.api.Assertions.assertThat(pendingComparison(store, id).path("status").asString())
                .isEqualTo(status);
    }

    @Test
    void isNotWorseThanBaseline_failsWithoutBaselineAndWritesPendingFile(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_failsWithoutBaselineAndWritesPendingFile");

        assertThatThrownBy(() -> assertThat(reportOf(true)).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessage("No baseline yet for " + id + ", 1 samples pending review.\n\n" + reviewHint(store, id));

        org.assertj.core.api.Assertions.assertThat(store.pendingFileOf(id)).exists();
        assertStatus(store, id, "new");
    }

    @Test
    void isNotWorseThanBaseline_listsEverySampleAsAddedOnFirstRun(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_listsEverySampleAsAddedOnFirstRun");

        assertThatThrownBy(() -> assertThat(reportOf(true, false)).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class);

        JsonNode added = pendingComparison(store, id).path("added");
        org.assertj.core.api.Assertions.assertThat(added).extracting(JsonNode::asString).containsExactly("A", "B");
    }

    @Test
    void isNotWorseThanBaseline_derivesIdFromTestMethod(@TempDir Path tempDir) {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);

        assertThatThrownBy(() -> assertThat(reportOf(true)).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class);

        org.assertj.core.api.Assertions
                .assertThat(tempDir.resolve("target/evaluation-baselines")
                        .resolve(EvaluationAssertionsTest.class.getName())
                        .resolve("isNotWorseThanBaseline_derivesIdFromTestMethod.json"))
                .exists();
    }

    @Test
    void isNotWorseThanBaseline_derivesIdInsideLambda(@TempDir Path tempDir) {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        Runnable run = () -> assertThat(reportOf(true)).isNotWorseThanBaseline(store, Optional.empty(), 5.0);

        assertThatThrownBy(run::run).isInstanceOf(AssertionError.class).hasMessageContaining("No baseline yet for");

        org.assertj.core.api.Assertions
                .assertThat(store.pendingFileOf(idOf("isNotWorseThanBaseline_derivesIdInsideLambda"))).exists();
    }

    @Test
    void isNotWorseThanBaseline_failsWhenNotInATest(@TempDir Path tempDir) throws InterruptedException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                assertThat(reportOf(true)).isNotWorseThanBaseline(store, Optional.empty(), 5.0);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        thread.start();
        thread.join();

        org.assertj.core.api.Assertions.assertThat(failure.get()).isInstanceOf(AssertionError.class)
                .hasMessage("Cannot tell which test is running: call isNotWorseThanBaseline from a JUnit test method, "
                        + "or pass an explicit id.");
    }

    @Test
    void isNotWorseThanBaseline_failsOnRegressionWithHint(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_failsOnRegressionWithHint");
        store.save(id, reportOf(true, true));

        assertThatThrownBy(() -> assertThat(reportOf(true, false)).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("B: 100.00% -> 0.00%")
                .hasMessageEndingWith("\n\n" + reviewHint(store, id));

        assertStatus(store, id, "failed");
    }

    @Test
    void isNotWorseThanBaseline_failsOnTrialsChange(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_failsOnTrialsChange");
        store.save(id, reportOf(true));
        EvaluationReport<String> threeTrials = new EvaluationReport<>(reportOf(true).evaluations().stream()
                .map(e -> (Scorer.EvaluationResult<String>) e).toList(), 3);

        assertThatThrownBy(() -> assertThat(threeTrials).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("Cannot compare " + id + " with its baseline: ")
                .hasMessageContaining("trials")
                .hasMessageContaining(". Accept this run to make it the new baseline.\n\n" + reviewHint(store, id));

        assertStatus(store, id, "incompatible");
    }

    @Test
    void isNotWorseThanBaseline_failsOnStrategyChange(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_failsOnStrategyChange");
        List<Scorer.EvaluationResult<String>> results = List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("A"), "x", EvaluationResult.fromBoolean(true)));
        store.save(id, new EvaluationReport<>(results, 1, List.of("one")));

        assertThatThrownBy(() -> assertThat(new EvaluationReport<>(results, 1, List.of("two")))
                .isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("Cannot compare " + id + " with its baseline: ")
                .hasMessageContaining("strategies");

        assertStatus(store, id, "incompatible");
    }

    @Test
    void isNotWorseThanBaseline_passesWithAddedSamplesAndMarksChanged(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_passesWithAddedSamplesAndMarksChanged");
        store.save(id, reportOf(true, true));

        assertThat(reportOf(true, true, true)).isNotWorseThanBaseline(store, Optional.empty(), 5.0);

        assertStatus(store, id, "changed");
        org.assertj.core.api.Assertions.assertThat(pendingComparison(store, id).path("added").get(0).asString())
                .isEqualTo("C");
    }

    @Test
    void isNotWorseThanBaseline_passesWithRemovedSamplesAndMarksChanged(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_passesWithRemovedSamplesAndMarksChanged");
        store.save(id, reportOf(true, true));

        assertThat(reportOf(true)).isNotWorseThanBaseline(store, Optional.empty(), 5.0);

        assertStatus(store, id, "changed");
        org.assertj.core.api.Assertions.assertThat(pendingComparison(store, id).path("removed").get(0).asString())
                .isEqualTo("B");
    }

    @Test
    void isNotWorseThanBaseline_unchangedRunHasUnchangedStatus(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_unchangedRunHasUnchangedStatus");
        store.save(id, reportOf(true, false));

        assertThat(reportOf(true, false)).isNotWorseThanBaseline(store, Optional.empty(), 5.0);

        assertStatus(store, id, "unchanged");
    }

    @Test
    void isNotWorseThanBaseline_advisoryOnlyChangeHasChangedStatus(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_advisoryOnlyChangeHasChangedStatus");
        store.save(id, withAdvisory(true));

        assertThat(withAdvisory(false)).isNotWorseThanBaseline(store, Optional.empty(), 0.0);

        assertStatus(store, id, "changed");
    }

    private static EvaluationReport<String> withAdvisory(boolean advisoryPassed) {
        EvaluationSample<String> a = sample("A");
        return new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(a, "x", EvaluationResult.fromBoolean(true)),
                Scorer.EvaluationResult.fromCompletedEvaluation(a, "x", EvaluationResult.fromBoolean(advisoryPassed),
                        true)));
    }

    @Test
    void isNotWorseThanBaseline_keepsNumericLookingOutputsAsText(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_keepsNumericLookingOutputsAsText");
        EvaluationSample<String> sample = new EvaluationSample<>("007", new Parameters(), "1.50", List.of());
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample, "007", EvaluationResult.fromBoolean(true))));

        assertThatThrownBy(() -> assertThat(report).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No baseline yet for " + id);

        PendingResult.SampleDetail detail = PendingResults.read(store.pendingFileOf(id)).details().get(0);
        org.assertj.core.api.Assertions.assertThat(detail.name()).isEqualTo("007");
        org.assertj.core.api.Assertions.assertThat(detail.expectedOutput()).isEqualTo("1.50");
        org.assertj.core.api.Assertions.assertThat(detail.responses().get(0).output()).isEqualTo("007");
        store.save(id, report);
        org.assertj.core.api.Assertions.assertThat(store.load(id)).isPresent();
    }

    @Test
    void isNotWorseThanBaseline_structurallyInvalidBaselineIsIncompatible(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_structurallyInvalidBaselineIsIncompatible");
        java.nio.file.Files.createDirectories(store.fileOf(id).getParent());
        java.nio.file.Files.writeString(store.fileOf(id), "{}");

        assertThatThrownBy(() -> assertThat(reportOf(false, false)).isNotWorseThanBaseline(store, Optional.empty(), 0.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("Cannot compare " + id + " with its baseline: the baseline file cannot be read: ");

        assertStatus(store, id, "incompatible");
    }

    @Test
    void isNotWorseThanBaseline_listsSamplesWithOnlyAdvisoryEvaluations(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_listsSamplesWithOnlyAdvisoryEvaluations");
        EvaluationReport<String> report = new EvaluationReport<>(List.of(Scorer.EvaluationResult
                .fromCompletedEvaluation(sample("J"), "x", EvaluationResult.fromBoolean(true), true)));

        assertThatThrownBy(() -> assertThat(report).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No baseline yet for " + id + ", 1 samples pending review.");

        org.assertj.core.api.Assertions.assertThat(pendingComparison(store, id).path("added"))
                .extracting(JsonNode::asString).containsExactly("J");
    }

    @Test
    void isNotWorseThanBaseline_replacesAPendingFileCutShortByAnEarlierRun(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_replacesAPendingFileCutShortByAnEarlierRun");
        java.nio.file.Files.createDirectories(store.pendingFileOf(id).getParent());
        java.nio.file.Files.writeString(store.pendingFileOf(id), "{\"id\": \"x\", \"run\": \"r");

        assertThatThrownBy(() -> assertThat(reportOf(true)).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("No baseline yet for " + id);

        assertStatus(store, id, "new");
        try (var files = java.nio.file.Files.list(store.pendingFileOf(id).getParent())) {
            org.assertj.core.api.Assertions.assertThat(files.map(f -> f.getFileName().toString()))
                    .containsExactly(store.pendingFileOf(id).getFileName().toString());
        }
    }

    @Test
    void isNotWorseThanBaseline_saysWhenEvaluationsEndedInAnError(@TempDir Path tempDir) {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_saysWhenEvaluationsEndedInAnError");
        RuntimeException quota = new RuntimeException("credit_balance_exhausted");
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromEvaluationThrowable(sample("A"), new AssertionError("Failed", quota)),
                Scorer.EvaluationResult.fromEvaluationThrowable(sample("B"), new AssertionError("Failed", quota))));

        assertThatThrownBy(() -> assertThat(report).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessage("No baseline yet for " + id + ", 2 samples pending review.\n\n"
                        + "2 of 2 evaluations ended in an error, so their scores say nothing about the answers:\n"
                        + "  - RuntimeException: credit_balance_exhausted (2)\n\n" + reviewHint(store, id));
    }

    @Test
    void isNotWorseThanBaseline_failsWhenSameIdAssertedTwice(@TempDir Path tempDir) {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        store.save("twice", reportOf(true));
        assertThat(reportOf(true)).isNotWorseThanBaseline(store, Optional.of("twice"), 5.0);

        assertThatThrownBy(() -> assertThat(reportOf(true)).isNotWorseThanBaseline(store, Optional.of("twice"), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("twice was already compared with its baseline in this run");
    }

    @Test
    void isNotWorseThanBaseline_unreadableBaselineIsIncompatible(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_unreadableBaselineIsIncompatible");
        java.nio.file.Files.createDirectories(store.fileOf(id).getParent());
        java.nio.file.Files.writeString(store.fileOf(id), "<<<<<<< HEAD\n{}\n=======\n{}\n>>>>>>> branch\n");

        assertThatThrownBy(() -> assertThat(reportOf(true)).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("Cannot compare " + id + " with its baseline: the baseline file cannot be read: ")
                .hasMessageEndingWith(". Accept this run to make it the new baseline.\n\n" + reviewHint(store, id));

        assertStatus(store, id, "incompatible");
        org.assertj.core.api.Assertions.assertThat(pendingComparison(store, id).path("incompatibility").asString())
                .startsWith("the baseline file cannot be read: ");
    }

    @Test
    void isNotWorseThanBaseline_acceptedResultIsTheNextBaseline(@TempDir Path tempDir) throws IOException {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        String id = idOf("isNotWorseThanBaseline_acceptedResultIsTheNextBaseline");
        assertThatThrownBy(() -> assertThat(reportOf(true, false)).isNotWorseThanBaseline(store, Optional.empty(), 5.0))
                .isInstanceOf(AssertionError.class);
        assertStatus(store, id, "new");

        Review.accept(PendingResults.read(store.pendingFileOf(id)));
        assertThat(reportOf(true, false)).isNotWorseThanBaseline(store, Optional.empty(), 5.0);

        assertStatus(store, id, "unchanged");
    }

    @Test
    void isNotWorseThanBaseline_failsOnDuplicateSampleNames(@TempDir Path tempDir) {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("A"), "x", EvaluationResult.fromBoolean(true)),
                Scorer.EvaluationResult.fromCompletedEvaluation(sample("A"), "x", EvaluationResult.fromBoolean(true))));

        assertThatThrownBy(() -> assertThat(report).isNotWorseThanBaseline(store, Optional.of("dup"), 5.0))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Sample names must be unique, found duplicates: A");

        org.assertj.core.api.Assertions.assertThat(store.pendingFileOf("dup")).doesNotExist();
    }

    private static EvaluationSample<String> sample(String name) {
        return new EvaluationSample<>(name, new Parameters(), "expected", List.of());
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
