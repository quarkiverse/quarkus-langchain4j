package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonReportFormatterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnCorrectFormat() {
        JsonReportFormatter formatter = new JsonReportFormatter();
        assertThat(formatter.format()).isEqualTo("json");
    }

    @Test
    void shouldReturnCorrectFileExtension() {
        JsonReportFormatter formatter = new JsonReportFormatter();
        assertThat(formatter.fileExtension()).isEqualTo(".json");
    }

    @Test
    void shouldFormatReportWithPrettyPrint() {
        // Create test data
        Parameters params1 = new Parameters();
        params1.add(new Parameter.UnnamedParameter("input1"));
        EvaluationSample<String> sample1 = new EvaluationSample<>("Sample1", params1, "expected1", List.of("tag1"));

        Parameters params2 = new Parameters();
        params2.add(new Parameter.UnnamedParameter("input2"));
        EvaluationSample<String> sample2 = new EvaluationSample<>("Sample2", params2, "expected2", List.of("tag2"));

        Scorer.EvaluationResult<String> result1 = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample1, "expected1", EvaluationResult.fromBoolean(true));
        Scorer.EvaluationResult<String> result2 = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample2, "actual2", EvaluationResult.fromBoolean(false));

        EvaluationReport<String> report = new EvaluationReport<>(List.of(result1, result2));

        // Format with pretty print
        JsonReportFormatter formatter = new JsonReportFormatter();
        String json = formatter.format(report, Map.of("pretty", true));

        // Verify JSON structure
        assertThat(json).contains("\"globalScore\": 50.00");
        assertThat(json).contains("\"totalSamples\": 2");
        assertThat(json).contains("\"passed\": 1");
        assertThat(json).contains("\"failed\": 1");
        assertThat(json).contains("\"tagScores\"");
        assertThat(json).contains("\"tag1\": 100.00");
        assertThat(json).contains("\"tag2\": 0.00");
        assertThat(json).contains("\"evaluations\"");
        assertThat(json).contains("\"name\": \"Sample1\"");
        assertThat(json).contains("\"name\": \"Sample2\"");
        assertThat(json).contains("\"expectedOutput\": \"expected1\"");
        assertThat(json).contains("\"actualOutput\": \"expected1\"");
        assertThat(json).contains("\"actualOutput\": \"actual2\"");

        // Verify pretty printing (newlines and indentation)
        assertThat(json).contains("\n");
        assertThat(json).contains("  ");
    }

    @Test
    void shouldFormatReportWithoutPrettyPrint() {
        // Create test data
        Parameters params = new Parameters();
        params.add(new Parameter.UnnamedParameter("input"));
        EvaluationSample<String> sample = new EvaluationSample<>("Sample", params, "expected", List.of());

        Scorer.EvaluationResult<String> result = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample, "actual", EvaluationResult.fromBoolean(true));

        EvaluationReport<String> report = new EvaluationReport<>(List.of(result));

        // Format without pretty print
        JsonReportFormatter formatter = new JsonReportFormatter();
        String json = formatter.format(report, Map.of("pretty", false));

        // Verify compact format (no newlines)
        assertThat(json).doesNotContain("\n");
        assertThat(json).contains("\"globalScore\":");
        assertThat(json).contains("\"totalSamples\": 1");
    }

    @Test
    void shouldEscapeSpecialCharactersInJson() {
        // Create test data with special characters
        Parameters params = new Parameters();
        params.add(new Parameter.UnnamedParameter("input"));
        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample\"With\"Quotes",
                params,
                "expected\nwith\nnewlines\tand\ttabs",
                List.of("tag\\with\\backslash"));

        Scorer.EvaluationResult<String> result = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample,
                "actual\"value\"",
                EvaluationResult.fromBoolean(true));

        EvaluationReport<String> report = new EvaluationReport<>(List.of(result));

        // Format
        JsonReportFormatter formatter = new JsonReportFormatter();
        String json = formatter.format(report, Map.of());

        // Verify escaping
        assertThat(json).contains("\\\""); // Escaped quotes
        assertThat(json).contains("\\n"); // Escaped newlines
        assertThat(json).contains("\\t"); // Escaped tabs
        assertThat(json).contains("\\\\"); // Escaped backslashes
    }

    @Test
    void shouldWriteReportToFile() throws IOException {
        // Create test data
        Parameters params = new Parameters();
        params.add(new Parameter.UnnamedParameter("input"));
        EvaluationSample<String> sample = new EvaluationSample<>("Sample", params, "expected", List.of());

        Scorer.EvaluationResult<String> result = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample, "actual", EvaluationResult.fromBoolean(true));

        EvaluationReport<String> report = new EvaluationReport<>(List.of(result));

        // Write to file
        JsonReportFormatter formatter = new JsonReportFormatter();
        Path outputFile = tempDir.resolve("report.json");
        formatter.write(report, outputFile, Map.of("pretty", true));

        // Verify file was created and contains valid JSON
        assertThat(outputFile).exists();
        String content = Files.readString(outputFile);
        assertThat(content).startsWith("{");
        assertThat(content).endsWith("}");
        assertThat(content).contains("\"globalScore\":");
    }

    @Test
    void shouldHandleReportWithNoTags() {
        // Create test data without tags
        Parameters params = new Parameters();
        params.add(new Parameter.UnnamedParameter("input"));
        EvaluationSample<String> sample = new EvaluationSample<>("Sample", params, "expected", List.of());

        Scorer.EvaluationResult<String> result = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample, "actual", EvaluationResult.fromBoolean(true));

        EvaluationReport<String> report = new EvaluationReport<>(List.of(result));

        // Format
        JsonReportFormatter formatter = new JsonReportFormatter();
        String json = formatter.format(report, Map.of());

        // Verify no tagScores section
        assertThat(json).contains("\"globalScore\":");
        assertThat(json).contains("\"evaluations\":");
        assertThat(json).doesNotContain("\"tagScores\":");
    }

    @Test
    void shouldHandleMultipleTags() {
        // Create test data with multiple tags
        Parameters params = new Parameters();
        params.add(new Parameter.UnnamedParameter("input"));
        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample",
                params,
                "expected",
                List.of("tag1", "tag2", "tag3"));

        Scorer.EvaluationResult<String> result = Scorer.EvaluationResult.fromCompletedEvaluation(
                sample, "actual", EvaluationResult.fromBoolean(true));

        EvaluationReport<String> report = new EvaluationReport<>(List.of(result));

        // Format
        JsonReportFormatter formatter = new JsonReportFormatter();
        String json = formatter.format(report, Map.of("pretty", true));

        // Verify all tags are included
        assertThat(json).contains("\"tags\": [\"tag1\", \"tag2\", \"tag3\"]");
    }
}
