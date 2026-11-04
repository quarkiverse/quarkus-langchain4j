package io.quarkiverse.langchain4j.testing.evaluation.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.langchain4j.testing.evaluation.review.BaselineSummary.Count;
import io.quarkiverse.langchain4j.testing.evaluation.review.BaselineSummary.Sample;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class BaselineSummaryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String REPORT = """
            {
              "globalScore": 50.00,
              "trials": 2,
              "strategies": ["Exact", "Judge (advisory)"],
              "evaluations": [
                {"name": "A", "passed": true, "advisory": false, "expectedOutput": "x", "actualOutput": "x"},
                {"name": "A", "passed": false, "advisory": false, "expectedOutput": "x", "actualOutput": "y"},
                {"name": "A", "passed": true, "advisory": true, "expectedOutput": "x", "actualOutput": "x"},
                {"name": "A", "passed": true, "advisory": true, "expectedOutput": "x", "actualOutput": "x"},
                {"name": "B", "passed": true, "advisory": false, "expectedOutput": "x", "actualOutput": "x"},
                {"name": "B", "passed": true, "advisory": false, "expectedOutput": "x", "actualOutput": "x"}
              ]
            }
            """;

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    @Test
    void fromReportCountsBlockingAndAdvisoryPerSample() throws Exception {
        BaselineSummary summary = BaselineSummary.fromReport(json(REPORT));

        assertThat(summary.trials()).isEqualTo(2);
        assertThat(summary.strategies()).containsExactly("Exact", "Judge (advisory)");
        assertThat(summary.samples()).containsExactly(
                new Sample("A", List.of(), new Count(1, 2), new Count(2, 2)),
                new Sample("B", List.of(), new Count(2, 2), new Count(0, 0)));
    }

    @Test
    void fromReportKeepsTags() throws Exception {
        BaselineSummary summary = BaselineSummary.fromReport(json("""
                {"trials": 1, "evaluations": [
                  {"name": "A", "passed": true, "advisory": false, "tags": ["t1", "t2"]}]}
                """));

        assertThat(summary.samples().get(0).tags()).containsExactly("t1", "t2");
    }

    @Test
    void fromReportWithoutStrategiesGivesEmptyList() throws Exception {
        BaselineSummary summary = BaselineSummary.fromReport(json("""
                {"trials": 1, "evaluations": [{"name": "A", "passed": true, "advisory": false}]}
                """));

        assertThat(summary.strategies()).isEmpty();
    }

    @Test
    void writeThenReadRoundTrips(@TempDir Path dir) throws Exception {
        BaselineSummary original = BaselineSummary.fromReport(json(REPORT));
        Path file = dir.resolve("nested/dir/id.json");

        original.write(file);

        assertThat(BaselineSummary.read(file)).isEqualTo(original);
    }

    @Test
    void writtenFileStartsWithFormatVersion(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("id.json");

        BaselineSummary.fromReport(json(REPORT)).write(file);

        JsonNode root = MAPPER.readTree(file.toFile());
        assertThat(root.propertyNames().iterator().next()).isEqualTo("formatVersion");
        assertThat(root.get("formatVersion").asInt()).isEqualTo(1);
        assertThat(Files.readString(file)).contains("\n");
    }

    @Test
    void isSummaryDistinguishesSummaryFromFullReport(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("id.json");
        BaselineSummary.fromReport(json(REPORT)).write(file);

        assertThat(BaselineSummary.isSummary(MAPPER.readTree(file.toFile()))).isTrue();
        assertThat(BaselineSummary.isSummary(json(REPORT))).isFalse();
    }

    @Test
    void readRejectsSummaryWithoutSamplesOrCounts() {
        assertThatThrownBy(() -> BaselineSummary.read(json("{\"formatVersion\": 1}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BaselineSummary.read(json("""
                {"formatVersion": 1, "samples": [{"name": "A"}]}""")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
