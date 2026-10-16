package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class EvaluationReportTest {

    @Test
    void globalScoreShouldBeZeroForEmptyEvaluations() {
        EvaluationReport<String> report = new EvaluationReport<>(List.of());

        assertThat(report.score()).isEqualTo(0.0);
        assertThat(report.evaluations()).isEmpty();
    }

    @Test
    void globalScoreShouldBeCorrect() {
        // Create mock evaluations.
        Scorer.EvaluationResult<String> result1 = Scorer.EvaluationResult.fromCompletedEvaluation(
                new EvaluationSample<>("Sample1", new Parameters(), "expected", List.of("tag1")),
                "expected",
                EvaluationResult.fromBoolean(true));

        Scorer.EvaluationResult<String> result2 = Scorer.EvaluationResult.fromCompletedEvaluation(
                new EvaluationSample<>("Sample2", new Parameters(), "expected", List.of("tag2")),
                "some-response",
                EvaluationResult.fromBoolean(false));

        EvaluationReport report = new EvaluationReport(List.of(result1, result2));

        // Assertions
        assertThat(report.score()).isEqualTo(50.0); // 1 passed out of 2.
    }

    @Test
    void scoreForTagShouldBeCorrect() {
        // Create mock evaluations.
        Scorer.EvaluationResult<String> result1 = Scorer.EvaluationResult.fromCompletedEvaluation(
                new EvaluationSample<>("Sample1", new Parameters(), "expected", List.of("tag1")),
                "expected",
                EvaluationResult.fromBoolean(true));

        Scorer.EvaluationResult<String> result2 = Scorer.EvaluationResult.fromCompletedEvaluation(
                new EvaluationSample<>("Sample2", new Parameters(), "expected", List.of("tag2")),
                "some-response",
                EvaluationResult.fromBoolean(false));

        Scorer.EvaluationResult<String> result3 = Scorer.EvaluationResult.fromCompletedEvaluation(
                new EvaluationSample<>("Sample3", new Parameters(), "expected", List.of("tag1", "tag2")),
                "expected",
                EvaluationResult.fromBoolean(true));

        EvaluationReport report = new EvaluationReport(List.of(result1, result2, result3));

        // Assertions
        assertThat(report.scoreForTag("tag1")).isEqualTo(100.0); // Both tag1 samples passed.
        assertThat(report.scoreForTag("tag2")).isEqualTo(50.0); // 1 passed out of 2 for tag2.
    }

    @Test
    void writeReportShouldGenerateMarkdownFile() throws IOException {
        // Create mock evaluations.
        Scorer.EvaluationResult<String> result1 = Scorer.EvaluationResult.fromCompletedEvaluation(
                new EvaluationSample<>("Sample1", new Parameters(), "expected", List.of("tag1")),
                "expected",
                EvaluationResult.fromBoolean(true));

        Scorer.EvaluationResult<String> result2 = Scorer.EvaluationResult.fromCompletedEvaluation(
                new EvaluationSample<>("Sample2", new Parameters(), "expected", List.of("tag2")),
                "some-response",
                EvaluationResult.fromBoolean(false));

        EvaluationReport report = new EvaluationReport(List.of(result1, result2));

        // Write the report to a temporary file.
        File tempFile = File.createTempFile("evaluation-report", ".md");
        report.writeReport(tempFile);

        // Assertions
        assertThat(tempFile).exists();
        String content = Files.readString(tempFile.toPath());
        assertThat(content).contains("# Evaluation Report");
        assertThat(content).contains("**Global Score**: 50.0");
        assertThat(content).contains("## Score per tags");
        assertThat(content).contains("- **tag1**: 100.0");
        assertThat(content).contains("- **tag2**: 0.0");
        assertThat(content).contains("## Details");
        assertThat(content).contains("- Sample1: PASSED");
        assertThat(content).contains("- Sample2: FAILED");
    }

    @Test
    void writeReportShouldGenerateMarkdownFileIncudingExpectedOutputAndResult() throws IOException {
        // Create mock evaluations.
        Scorer.EvaluationResult<String> result1 = Scorer.EvaluationResult.fromCompletedEvaluation(
                new EvaluationSample<>("Sample1", new Parameters(), "expected1", List.of("tag1")),
                "expected1",
                EvaluationResult.fromBoolean(true));

        Scorer.EvaluationResult<String> result2 = Scorer.EvaluationResult.fromCompletedEvaluation(
                new EvaluationSample<>("Sample2", new Parameters(), "expected2", List.of("tag2")),
                "some-response",
                EvaluationResult.fromBoolean(false));

        EvaluationReport report = new EvaluationReport(List.of(result1, result2));

        // Write the report to a temporary file.
        File tempFile = File.createTempFile("evaluation-report", ".md");
        report.writeReport(tempFile, true);

        // Assertions
        assertThat(tempFile).exists();
        String content = Files.readString(tempFile.toPath());
        assertThat(content).contains("# Evaluation Report");
        assertThat(content).contains("**Global Score**: 50.0");
        assertThat(content).contains("## Score per tags");
        assertThat(content).contains("- **tag1**: 100.0");
        assertThat(content).contains("- **tag2**: 0.0");
        assertThat(content).contains("## Details");
        assertThat(content).contains("### Sample1: PASSED");
        assertThat(content).contains("#### Result\nexpected1");
        assertThat(content).contains("#### Expected Output\nexpected1");
        assertThat(content).contains("### Sample2: FAILED");
        assertThat(content).contains("#### Result\nsome-response");
        assertThat(content).contains("#### Expected Output\nexpected2");
    }

    @Test
    void confidenceIntervalShouldBeComputedOverSamples() {
        // 10 samples, 8 pass and 2 fail: mean 80%, standard error of the mean across samples 13.33 points
        List<Scorer.EvaluationResult<String>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(Scorer.EvaluationResult.fromCompletedEvaluation(
                    new EvaluationSample<>("Sample" + i, new Parameters(), "expected", List.of(i < 5 ? "first" : "second")),
                    "x",
                    EvaluationResult.fromBoolean(i < 8)));
        }
        EvaluationReport<String> report = new EvaluationReport<>(results);

        assertThat(report.trials()).isEqualTo(1);
        assertThat(report.score()).isEqualTo(80.0);
        assertThat(report.standardError()).isCloseTo(13.33, within(0.01));
        assertThat(report.confidenceInterval().low()).isCloseTo(53.87, within(0.01));
        assertThat(report.confidenceInterval().high()).isEqualTo(100.0);

        // Tag "first" has 5 passing samples, tag "second" has 3 passing and 2 failing ones
        assertThat(report.standardErrorForTag("first")).isEqualTo(0.0);
        assertThat(report.confidenceIntervalForTag("first")).isEqualTo(new EvaluationReport.Interval(100.0, 100.0));
        assertThat(report.scoreForTag("second")).isEqualTo(60.0);
        assertThat(report.standardErrorForTag("second")).isCloseTo(24.49, within(0.01));
        assertThat(report.confidenceIntervalForTag("second").low()).isCloseTo(12.0, within(0.01));
    }

    @Test
    void confidenceIntervalWithTrialsShouldUsePerSampleMeans() {
        // Two samples with 4 trials each: A passes 2 of 4, B passes 4 of 4
        List<Scorer.EvaluationResult<String>> results = new ArrayList<>();
        EvaluationSample<String> a = new EvaluationSample<>("A", new Parameters(), "expected", List.of());
        EvaluationSample<String> b = new EvaluationSample<>("B", new Parameters(), "expected", List.of());
        for (int trial = 0; trial < 4; trial++) {
            results.add(Scorer.EvaluationResult.fromCompletedEvaluation(a, "x", EvaluationResult.fromBoolean(trial < 2)));
        }
        for (int trial = 0; trial < 4; trial++) {
            results.add(Scorer.EvaluationResult.fromCompletedEvaluation(b, "x", EvaluationResult.fromBoolean(true)));
        }
        EvaluationReport<String> report = new EvaluationReport<>(results, 4);

        assertThat(report.trials()).isEqualTo(4);
        assertThat(report.score()).isEqualTo(75.0);
        assertThat(report.scoreForSample("A")).isEqualTo(50.0);
        assertThat(report.scoreForSample("B")).isEqualTo(100.0);
        assertThat(report.scoreForSample("unknown")).isEqualTo(0.0);
        // Per-sample rates 0.5 and 1.0: mean 0.75, sample variance 0.125, SE = sqrt(0.125 / 2) = 0.25.
        // Pooling the 8 trials would give sqrt(0.75 * 0.25 / 8) = 0.153 instead.
        assertThat(report.standardError()).isCloseTo(25.0, within(0.01));
        assertThat(report.confidenceInterval().low()).isCloseTo(26.0, within(0.01));
        assertThat(report.confidenceInterval().high()).isEqualTo(100.0);
    }

    @Test
    void advisoryEvaluationsShouldNotAffectScores() {
        EvaluationSample<String> a = new EvaluationSample<>("A", new Parameters(), "expected", List.of("t"));
        EvaluationSample<String> b = new EvaluationSample<>("B", new Parameters(), "expected", List.of("t"));
        List<Scorer.EvaluationResult<String>> results = List.of(
                blocking(a, true), advisory(a, false),
                blocking(b, true), advisory(b, true));
        EvaluationReport<String> report = new EvaluationReport<>(results);

        assertThat(report.score()).isEqualTo(100.0);
        assertThat(report.scoreForTag("t")).isEqualTo(100.0);
        assertThat(report.scoreForSample("A")).isEqualTo(100.0);
        assertThat(report.standardError()).isEqualTo(0.0);
        assertThat(report.confidenceInterval()).isEqualTo(new EvaluationReport.Interval(100.0, 100.0));
        assertThat(report.advisoryScore()).isEqualTo(50.0);
    }

    @Test
    void advisoryScoreShouldBeZeroWithoutAdvisoryEvaluations() {
        EvaluationSample<String> a = new EvaluationSample<>("A", new Parameters(), "expected", List.of());
        EvaluationReport<String> report = new EvaluationReport<>(List.of(blocking(a, true)));

        assertThat(report.advisoryScore()).isEqualTo(0.0);
        assertThat(report.score()).isEqualTo(100.0);
    }

    @Test
    void scoreShouldBeZeroWhenAllEvaluationsAreAdvisory() {
        EvaluationSample<String> a = new EvaluationSample<>("A", new Parameters(), "expected", List.of());
        EvaluationReport<String> report = new EvaluationReport<>(List.of(advisory(a, true)));

        assertThat(report.score()).isEqualTo(0.0);
        assertThat(report.advisoryScore()).isEqualTo(100.0);
    }

    private static Scorer.EvaluationResult<String> blocking(EvaluationSample<String> sample, boolean passed) {
        return Scorer.EvaluationResult.fromCompletedEvaluation(sample, "x", EvaluationResult.fromBoolean(passed));
    }

    private static Scorer.EvaluationResult<String> advisory(EvaluationSample<String> sample, boolean passed) {
        return Scorer.EvaluationResult.fromCompletedEvaluation(sample, "x", EvaluationResult.fromBoolean(passed), true);
    }

    @Test
    void trialsMustBeAtLeastOne() {
        assertThatThrownBy(() -> new EvaluationReport<String>(List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Trials must be at least 1");
    }

    @Test
    void confidenceIntervalShouldCollapseWithFewerThanTwoSamples() {
        Scorer.EvaluationResult<String> result = Scorer.EvaluationResult.fromCompletedEvaluation(
                new EvaluationSample<>("Only", new Parameters(), "expected", List.of()),
                "expected",
                EvaluationResult.fromBoolean(true));
        EvaluationReport<String> report = new EvaluationReport<>(List.of(result));

        assertThat(report.standardError()).isEqualTo(0.0);
        assertThat(report.confidenceInterval()).isEqualTo(new EvaluationReport.Interval(100.0, 100.0));
        assertThat(new EvaluationReport<String>(List.of()).confidenceInterval())
                .isEqualTo(new EvaluationReport.Interval(0.0, 0.0));
    }
}
