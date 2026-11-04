package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationSnapshotTest {

    @TempDir
    Path tempDir;

    @Test
    void snapshotOfReportShouldKeepOneRecordPerEvaluation() {
        EvaluationReport<String> report = report();

        EvaluationSnapshot snapshot = EvaluationSnapshot.of(report);

        assertThat(snapshot.trials()).isEqualTo(2);
        assertThat(snapshot.records()).containsExactly(
                new EvaluationSnapshot.Record("A", List.of("t1"), true, false),
                new EvaluationSnapshot.Record("A", List.of("t1"), false, false),
                new EvaluationSnapshot.Record("B", List.of(), true, false),
                new EvaluationSnapshot.Record("B", List.of(), false, true));
    }

    @Test
    void snapshotShouldRoundTripThroughTheJsonReport() throws IOException {
        EvaluationReport<String> report = report();
        Path file = tempDir.resolve("report.json");
        new JsonReportFormatter().write(report, file, Map.of());

        EvaluationSnapshot snapshot = EvaluationSnapshot.read(file);

        assertThat(snapshot).isEqualTo(EvaluationSnapshot.of(report));
    }

    @Test
    void sampleScoresShouldBeComputedOverBlockingRecords() {
        EvaluationSnapshot snapshot = EvaluationSnapshot.of(report());

        assertThat(snapshot.sampleScores()).containsExactly(
                Map.entry("A", 50.0),
                Map.entry("B", 100.0));
    }

    @Test
    void readsSummaryFormat() throws IOException {
        Path file = tempDir.resolve("summary.json");
        Files.writeString(file, """
                {"formatVersion": 1, "trials": 2, "strategies": ["Exact"],
                 "samples": [{"name": "A", "tags": ["t1"],
                              "blocking": {"passed": 1, "total": 2}, "advisory": {"passed": 0, "total": 0}}]}
                """);

        EvaluationSnapshot snapshot = EvaluationSnapshot.read(file);

        assertThat(snapshot.trials()).isEqualTo(2);
        assertThat(snapshot.strategies()).containsExactly("Exact");
        assertThat(snapshot.sampleScores()).containsExactly(Map.entry("A", 50.0));
        assertThat(snapshot.records()).containsExactly(
                new EvaluationSnapshot.Record("A", List.of("t1"), true, false),
                new EvaluationSnapshot.Record("A", List.of("t1"), false, false));
    }

    @Test
    void readsBranchFullReportFormat() throws IOException {
        Path file = tempDir.resolve("full.json");
        Files.writeString(file, """
                {"trials": 1, "evaluations": [
                  {"name": "A", "tags": ["t"], "passed": true, "advisory": false}]}
                """);

        EvaluationSnapshot snapshot = EvaluationSnapshot.read(file);

        assertThat(snapshot.strategies()).isEmpty();
        assertThat(snapshot.records()).containsExactly(new EvaluationSnapshot.Record("A", List.of("t"), true, false));
    }

    @Test
    void carriesStrategies() throws IOException {
        EvaluationSample<String> a = new EvaluationSample<>("A", new Parameters(), "expected", List.of());
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(a, "x", EvaluationResult.fromBoolean(true))),
                1, List.of("Exact", "Judge (advisory)"));
        Path file = tempDir.resolve("report.json");
        new JsonReportFormatter().write(report, file, Map.of());

        assertThat(EvaluationSnapshot.of(report).strategies()).containsExactly("Exact", "Judge (advisory)");
        assertThat(EvaluationSnapshot.read(file).strategies()).containsExactly("Exact", "Judge (advisory)");
    }

    private static EvaluationReport<String> report() {
        EvaluationSample<String> a = new EvaluationSample<>("A", new Parameters(), "expected", List.of("t1"));
        EvaluationSample<String> b = new EvaluationSample<>("B", new Parameters(), "expected", List.of());
        return new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(a, "expected", EvaluationResult.fromBoolean(true)),
                Scorer.EvaluationResult.fromCompletedEvaluation(a, "other", EvaluationResult.fromBoolean(false)),
                Scorer.EvaluationResult.fromCompletedEvaluation(b, "expected", EvaluationResult.fromBoolean(true)),
                Scorer.EvaluationResult.fromCompletedEvaluation(b, "expected", EvaluationResult.failed("judge"), true)),
                2);
    }
}
