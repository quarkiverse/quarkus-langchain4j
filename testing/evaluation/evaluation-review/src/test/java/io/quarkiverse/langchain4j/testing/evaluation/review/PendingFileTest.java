package io.quarkiverse.langchain4j.testing.evaluation.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PendingFileTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private static Comparison full() {
        return new Comparison(ResultStatus.FAILED, 5.0, OptionalDouble.of(-12.5), OptionalDouble.of(2.0),
                OptionalDouble.of(-16.4), OptionalDouble.of(-8.6),
                List.of(new SampleChange("A", 100, 50), new SampleChange("B", 80, 80)),
                List.of("C"), List.of("D"), Optional.empty(), OptionalDouble.of(-1.0));
    }

    @Test
    void writesIdRunBaselineFileComparisonAndReport() throws Exception {
        Path file = tempDir.resolve("nested/faq.json");

        PendingFile.write(file, "a.B/faq", "42-x", "src/test/resources/evaluation-baselines/a.B/faq.json",
                "{\"trials\":3}", full());

        JsonNode root = MAPPER.readTree(file.toFile());
        assertThat(root.get(PendingFile.ID).asString()).isEqualTo("a.B/faq");
        assertThat(root.get(PendingFile.RUN).asString()).isEqualTo("42-x");
        assertThat(root.get(PendingFile.BASELINE_FILE).asString())
                .isEqualTo("src/test/resources/evaluation-baselines/a.B/faq.json");
        assertThat(root.get(PendingFile.COMPARISON).get(PendingFile.STATUS).asString()).isEqualTo("failed");
        assertThat(root.get(PendingFile.REPORT).get("trials").asInt()).isEqualTo(3);
        assertThat(PendingFile.runOf(file)).contains("42-x");
    }

    @Test
    void comparisonSurvivesARoundTrip() throws Exception {
        Path file = tempDir.resolve("faq.json");
        PendingFile.write(file, "faq", "r", "b.json", "{}", full());

        assertThat(PendingFile.readComparison(MAPPER.readTree(file.toFile()).get(PendingFile.COMPARISON)))
                .isEqualTo(full());
    }

    @Test
    void absentOptionalsAreOmittedAndStayAbsent() throws Exception {
        Comparison incompatible = new Comparison(ResultStatus.INCOMPATIBLE, 5.0, OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), List.of(), List.of(),
                List.of(), Optional.of("different trials"), OptionalDouble.empty());
        Path file = tempDir.resolve("faq.json");

        PendingFile.write(file, "faq", "r", "b.json", "{}", incompatible);

        JsonNode node = MAPPER.readTree(file.toFile()).get(PendingFile.COMPARISON);
        assertThat(node.has(PendingFile.MEAN_DIFFERENCE)).isFalse();
        assertThat(node.get(PendingFile.STATUS).asString()).isEqualTo("incompatible");
        assertThat(PendingFile.readComparison(node)).isEqualTo(incompatible);
    }

    @Test
    void runOfMissingFileIsEmpty() {
        assertThat(PendingFile.runOf(tempDir.resolve("none.json"))).isEmpty();
    }
}
