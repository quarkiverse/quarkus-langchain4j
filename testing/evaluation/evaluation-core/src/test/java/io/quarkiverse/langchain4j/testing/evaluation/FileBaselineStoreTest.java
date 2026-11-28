package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.langchain4j.testing.evaluation.review.Comparison;
import io.quarkiverse.langchain4j.testing.evaluation.review.PendingFile;
import io.quarkiverse.langchain4j.testing.evaluation.review.ResultStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class FileBaselineStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadShouldBeEmptyWhenThereIsNoBaseline() {
        BaselineStore store = new FileBaselineStore(tempDir.resolve("baselines"));

        assertThat(store.load("faq")).isEmpty();
    }

    @Test
    void saveThenLoadShouldReturnTheSnapshotOfTheReport() throws Exception {
        Path directory = tempDir.resolve("baselines");
        BaselineStore store = new FileBaselineStore(directory);
        EvaluationSample<String> sample = new EvaluationSample<>("A", new Parameters(), "expected", List.of("t"));
        EvaluationReport<String> report = new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample, "expected", EvaluationResult.fromBoolean(true))));

        store.save("faq", report);

        assertThat(directory.resolve("faq.json")).exists();
        assertThat(new ObjectMapper().readTree(directory.resolve("faq.json").toFile()).path("formatVersion").asInt())
                .isEqualTo(1);
        assertThat(store.load("faq")).contains(EvaluationSnapshot.of(report));
    }

    private static EvaluationReport<String> report() {
        EvaluationSample<String> sample = new EvaluationSample<>("A", new Parameters(), "expected", List.of());
        return new EvaluationReport<>(List.of(
                Scorer.EvaluationResult.fromCompletedEvaluation(sample, "expected", EvaluationResult.fromBoolean(true))));
    }

    private static Comparison comparison() {
        return new Comparison(ResultStatus.NEW, 5.0, OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), List.of(), List.of(), List.of(), Optional.empty(),
                OptionalDouble.empty());
    }

    @Test
    void conventionalUsesTargetWithoutGradleBuildFile() {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);

        assertThat(store.fileOf("a.B/faq")).isEqualTo(tempDir.resolve("src/test/resources/evaluation-baselines/a.B/faq.json"));
        assertThat(store.pendingFileOf("a.B/faq")).isEqualTo(tempDir.resolve("target/evaluation-baselines/a.B/faq.json"));
    }

    @Test
    void conventionalUsesBuildWithBuildGradleKts() throws Exception {
        Files.createFile(tempDir.resolve("build.gradle.kts"));

        assertThat(FileBaselineStore.conventional(tempDir).pendingFileOf("faq"))
                .isEqualTo(tempDir.resolve("build/evaluation-baselines/faq.json"));
    }

    @Test
    void conventionalUsesBuildWithBuildGradle() throws Exception {
        Files.createFile(tempDir.resolve("build.gradle"));

        assertThat(FileBaselineStore.conventional(tempDir).pendingFileOf("faq"))
                .isEqualTo(tempDir.resolve("build/evaluation-baselines/faq.json"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "pom.xml", "build.gradle", "build.gradle.kts" })
    void receiveWritesPendingFileAndReturnsReviewHint(String buildFile) throws Exception {
        Files.createFile(tempDir.resolve(buildFile));
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);

        String hint = store.receive("a.B/faq", report(), comparison());

        assertThat(hint).contains("Pending result: " + store.pendingFileOf("a.B/faq"),
                "Baseline: " + store.fileOf("a.B/faq"),
                "Run `quarkus langchain4j-eval review` to accept or reject it.",
                "Manual review (no CLI): https://docs.quarkiverse.io/quarkus-langchain4j/dev/testing.html#reviewing-results-manually");
        Path pending = store.pendingFileOf("a.B/faq");
        JsonNode root = new ObjectMapper().readTree(pending.toFile());
        assertThat(root.get("id").asString()).isEqualTo("a.B/faq");
        assertThat(root.get("run").asString()).isEqualTo(FileBaselineStore.currentRun());
        assertThat(root.get("baselineFile").asString()).isEqualTo(store.fileOf("a.B/faq").toString());
        assertThat(root.get("comparison").get("status").asString()).isEqualTo("new");
        assertThat(root.get("report").has("trials")).isTrue();
        assertThat(store.fileOf("a.B/faq")).doesNotExist();
    }

    @Test
    void extractedPendingReportCanBeAcceptedWithoutTheCli() throws Exception {
        FileBaselineStore store = new FileBaselineStore(tempDir.resolve("approved scores"),
                tempDir.resolve("pending results"));
        EvaluationReport<String> report = report();
        store.receive("a.B/faq", report, comparison());
        ObjectMapper mapper = new ObjectMapper();
        JsonNode pending = mapper.readTree(store.pendingFileOf("a.B/faq").toFile());

        Files.createDirectories(store.fileOf("a.B/faq").getParent());
        Files.writeString(store.fileOf("a.B/faq"), mapper.writeValueAsString(pending.get("report")));
        Files.delete(store.pendingFileOf("a.B/faq"));

        EvaluationAssertions.assertThat(report).isNotWorseThan(store, "a.B/faq", 0.0);

        assertThat(mapper.readTree(store.pendingFileOf("a.B/faq").toFile()).path("comparison").path("status").asString())
                .isEqualTo("unchanged");
    }

    @Test
    void receiveTwiceInSameRunFails() {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        store.receive("faq", report(), comparison());

        assertThatThrownBy(() -> store.receive("faq", report(), comparison()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("faq was already compared with its baseline in this run. "
                        + "Pass an explicit id to isNotWorseThanBaseline(String) for each evaluation.");
    }

    @Test
    void receiveInAnotherRunOfTheSameJvmDoesNotCollide() {
        String previous = System.getProperty("quarkus.langchain4j.evaluation.run");
        try {
            FileBaselineStore store = FileBaselineStore.conventional(tempDir);
            System.setProperty("quarkus.langchain4j.evaluation.run", "first");
            store.receive("faq", report(), comparison());

            System.setProperty("quarkus.langchain4j.evaluation.run", "second");
            store.receive("faq", report(), comparison());

            assertThat(PendingFile.runOf(store.pendingFileOf("faq"))).contains("second");
            assertThatThrownBy(() -> store.receive("faq", report(), comparison()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already compared");
        } finally {
            if (previous == null) {
                System.clearProperty("quarkus.langchain4j.evaluation.run");
            } else {
                System.setProperty("quarkus.langchain4j.evaluation.run", previous);
            }
        }
    }

    @Test
    void receiveOverwritesPendingFileFromEarlierRun() {
        FileBaselineStore store = FileBaselineStore.conventional(tempDir);
        PendingFile.write(store.pendingFileOf("faq"), "faq", "other", "b.json", "{}", comparison());

        store.receive("faq", report(), comparison());

        assertThat(PendingFile.runOf(store.pendingFileOf("faq"))).contains(FileBaselineStore.currentRun());
    }

    @Test
    void pendingFileShouldDefaultToTarget() {
        FileBaselineStore store = new FileBaselineStore(Path.of("src/test/resources/baselines"));

        assertThat(store.pendingFileOf("faq")).isEqualTo(Path.of("target/evaluation-baselines/faq.json"));
    }
}
